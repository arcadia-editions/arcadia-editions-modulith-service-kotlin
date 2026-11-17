package com.arcadiaeditions

import com.arcadiaeditions.RetryLoopRecorder.Authorize
import com.arcadiaeditions.RetryLoopRecorder.Backoff
import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.repository.jpa.InventoryPositionRepository
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.config.DockerComposeInitializer
import com.arcadiaeditions.config.ExternalizationConfiguration
import com.arcadiaeditions.config.MutableClock
import com.arcadiaeditions.config.TestClock
import com.arcadiaeditions.config.TestClockConfiguration
import com.arcadiaeditions.fulfillment.shipping.FulfillmentRepository
import com.arcadiaeditions.fulfillment.shipping.domain.FulfillmentStatus
import com.arcadiaeditions.notifications.consumer.FakeEmailSender
import com.arcadiaeditions.notifications.consumer.domain.NotificationChannel
import com.arcadiaeditions.notifications.consumer.domain.NotificationStatus
import com.arcadiaeditions.notifications.consumer.domain.NotificationType
import com.arcadiaeditions.notifications.consumer.domain.events.OrderConfirmationSent
import com.arcadiaeditions.notifications.consumer.repository.jpa.NotificationRepository
import com.arcadiaeditions.orders.checkout.core.domain.Order
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.OrderItemInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.ShippingAddressInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.StartOrderCheckoutInput
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.domain.PaymentStatus
import com.arcadiaeditions.payments.processing.domain.event.PaymentAuthorized
import com.arcadiaeditions.payments.processing.domain.event.PaymentFailed
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetried
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.EnableScenarios
import org.springframework.modulith.test.Scenario
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * place-order-flow, path `retry` (place-order-flow.zfl:114-146, end 262-266): the first authorization fails
 * (pm_card_processorErrorOnce, shape of C-12), PaymentFailed -> retryPayment -> PaymentRetried -> reauthorizePayment
 * approves attempt 2 after the 5 s backoff, and the flow ends in OrderConfirmationSent (shape of C-06, payment attemptCount
 * 2). T-59: attempts are counted only by authorizePayment (1) and reauthorizePayment (2), never by retryPayment; the
 * backoff is asked before attempt 2 while the Payment is still FAILED ([RetryLoopTestConfiguration] records it instead of
 * sleeping); a redelivered PaymentFailed or PaymentRetried with a stale attemptCount changes nothing. Second test: the C-12
 * fixture (ORD-012, Payment FAILED attempt 1) resumes from its pending PaymentFailed. Not @Transactional: the module
 * listeners run after commit.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // T-43: no replay, at startup, of incomplete publications another context or run left in event_publication
    properties = ["spring.modulith.events.republish-outstanding-events-on-restart=false"],
)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
@EnableScenarios
@Import(TestClockConfiguration::class, RetryLoopTestConfiguration::class)
// T-43: a fresh context per flow test (schema + data.sql reloaded, new async threads). Scenario captures events through
// an InheritableThreadLocal, which async listener threads already created in a reused context never see.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// T-43: no resubmitIncompletePublications tick: its first run, right after startup, resubmits this test's in-flight
// publications and the listener runs twice (seen: two STOCK_UNAVAILABLE notifications for one order)
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowRetryIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var recorder: RetryLoopRecorder
    @Autowired lateinit var events: RetryLoopEvents
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    @BeforeEach
    fun reset() {
        clock.reset()
        recorder.reset()
    }

    /** C-06 input: the checkout of CUST-006, with the payment method that fails attempt 1 only. */
    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-006",
            customerEmail = "customer06@example.com",
            paymentMethodId = "pm_card_processorErrorOnce",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-001", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun processorErrorOnceIsRetriedAndEndsInOrderConfirmationSent(scenario: Scenario) {
        // data/jpa/InventoryPosition/1.json: SKU-001 available 89, reserved 6, sold 5 in a fresh context; read, not
        // asserted: counters are checked relative to these (T-43)
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }
        val mark = events.mark()
        var orderId: String? = null

        scenario
            .stimulate(Supplier { ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()) })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderConfirmationSent::class.java)
            .toArriveAndVerify(
                BiConsumer { event: OrderConfirmationSent, order: Order ->
                    orderId = order.orderId
                    assertThat(event.orderId).isEqualTo(order.orderId)
                    assertThat(event.customerId).isEqualTo("CUST-006")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer06@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                    assertEndState(order.orderId!!, available, reserved, sold)
                }
            )

        val id = orderId!!
        assertRetryLoop(id, mark)
        assertStaleRedeliveriesChangeNothing(id, mark)
    }

    /** attempt 1 by authorizePayment, the 5 s backoff while FAILED (attempt 1), attempt 2 by reauthorizePayment. */
    private fun assertRetryLoop(orderId: String, mark: Set<java.util.UUID>) {
        assertThat(recorder.steps)
            .containsExactly(
                Authorize(1, PaymentStatus.AUTHORIZATION_REQUESTED, 1),
                Backoff(Duration.ofSeconds(5), PaymentStatus.FAILED, 1),
                Authorize(2, PaymentStatus.AUTHORIZATION_REQUESTED, 2),
            )
        // retryPayment never increments: PaymentRetried carries the attemptCount of the PaymentFailed it answers
        assertThat(events.since(mark, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId })
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(1)
        assertThat(
                events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(1)
    }

    /** The PaymentFailed and PaymentRetried of attempt 1 delivered again once the Payment moved on: no-ops, no event. */
    private fun assertStaleRedeliveriesChangeNothing(orderId: String, mark: Set<java.util.UUID>) {
        val failed = events.since(mark, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId }.single()
        val retried =
            events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }.single()
        val payment = paymentRepository.findByOrderId(orderId)!!.snapshot()
        val steps = recorder.steps.toList()
        val redelivered = events.mark()

        events.redeliver(failed, timeout)
        events.redeliver(retried, timeout)

        assertThat(paymentRepository.findByOrderId(orderId)!!.snapshot()).isEqualTo(payment)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(recorder.steps).isEqualTo(steps)
        // only the two redelivered publications: no PaymentRetried, PaymentFailed or PaymentAuthorized of their own
        assertThat(events.since(redelivered, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId })
            .containsExactly(failed)
        assertThat(events.since(redelivered, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId })
            .containsExactly(retried)
        assertThat(events.since(redelivered, PaymentAuthorized::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }

    /**
     * C-12: ORD-012 is CREATED, RES-012 RESERVED, Payment FAILED at attempt 1 with PaymentFailed pending. A PaymentRetried
     * whose attemptCount is not the Payment's (2, out of order) is a no-op; the pending PaymentFailed (attempt 1) runs the
     * loop: retryPayment retried, the 5 s backoff, reauthorizePayment attempt 2 (pm_card_visa approves), and the flow ends
     * in OrderConfirmationSent for ORD-012.
     */
    @Test
    fun c12PendingPaymentFailedIsRetriedAndEndsInOrderConfirmationSent(scenario: Scenario) {
        val orderId = "ORD-012"
        recorder.orderId = orderId
        val fixture = paymentRepository.findByOrderId(orderId)!!
        assertThat(fixture.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(fixture.attemptCount!!.toInt()).isEqualTo(1)
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }
        val mark = events.mark()

        // out of order: attemptCount 2 is not the attempt that failed
        events.redeliver(
            PaymentRetried(orderId, "CUST-012", 2 as Integer, Instant.parse("2026-10-01T11:59:30Z")),
            timeout,
        )
        assertThat(paymentRepository.findByOrderId(orderId)!!.snapshot()).isEqualTo(fixture.snapshot())
        assertThat(recorder.steps).isEmpty()

        scenario
            .publish(PaymentFailed(orderId, "CUST-012", 1 as Integer, "processor_timeout", Instant.parse("2026-10-01T11:59:30Z")))
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderConfirmationSent::class.java)
            .matching(Predicate { it.orderId == orderId })
            .toArriveAndVerify(
                Consumer { event: OrderConfirmationSent ->
                    assertThat(event.customerId).isEqualTo("CUST-012")
                    assertThat(event.recipient).isEqualTo("customer12@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                }
            )

        assertThat(recorder.steps)
            .containsExactly(
                Backoff(Duration.ofSeconds(5), PaymentStatus.FAILED, 1),
                Authorize(2, PaymentStatus.AUTHORIZATION_REQUESTED, 2),
            )
        assertThat(
                events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactlyInAnyOrder(2, 1)
        await().atMost(timeout).untilAsserted {
            assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.CAPTURED)
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNotNull()
        }
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.attemptCount!!.toInt()).isEqualTo(2)
        assertThat(payment.authorizedAt).isEqualTo(TestClock.NOW)
        assertThat(payment.failedAt).isNull()
        assertThat(payment.failureReason).isNull()
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(stockReservationRepository.findByOrderId(orderId).single().status).isEqualTo(ReservationStatus.CONFIRMED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved - 1, sold + 1)
    }

    private fun assertEndState(orderId: String, available: Int, reserved: Int, sold: Int) {
        val now = TestClock.NOW
        // the event is published inside the sendOrderConfirmation transaction: wait for its commit
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION))
                .isNotNull()
        }

        // orders.checkout: startOrderCheckout -> CREATED, confirmOrder -> CONFIRMED
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(order.customerId).isEqualTo("CUST-006")
        assertThat(order.totalAmount).isEqualByComparingTo("49.99")
        assertThat(order.currency).isEqualTo("EUR")
        assertThat(order.createdAt).isEqualTo(now)
        assertThat(order.confirmedAt).isEqualTo(now)
        assertThat(order.cancelledAt).isNull()

        // catalog.inventory: reserveStock -> RESERVED, confirmStockReservation -> CONFIRMED (A-02, A-04)
        val reservations = stockReservationRepository.findByOrderId(orderId)
        assertThat(reservations).hasSize(1)
        val reservation = reservations.single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(reservation.sku).isEqualTo("SKU-001")
        assertThat(reservation.quantity).isEqualTo(1)
        assertThat(reservation.reservedAt).isEqualTo(now)
        assertThat(reservation.expiresAt).isEqualTo(now.plus(Duration.ofMinutes(10)))
        assertThat(reservation.confirmedAt).isEqualTo(now)
        assertThat(reservation.releasedAt).isNull()
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available - 1, reserved, sold + 1)

        // payments.processing: authorizePayment fails (attempt 1), retryPayment, reauthorizePayment -> AUTHORIZED
        // (attempt 2), capturePayment -> CAPTURED (A-01); the failure of attempt 1 is cleared
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(payment.attemptCount).isEqualTo(2)
        assertThat(payment.amount).isEqualByComparingTo("49.99")
        assertThat(payment.currency).isEqualTo("EUR")
        assertThat(payment.providerReference).isEqualTo("PSP-AUTH-" + orderId.removePrefix("ORD-"))
        assertThat(payment.authorizedAt).isEqualTo(now)
        assertThat(payment.capturedAt).isEqualTo(now)
        assertThat(payment.declinedAt).isNull()
        assertThat(payment.failedAt).isNull()
        assertThat(payment.failureReason).isNull()
        assertThat(payment.voidedAt).isNull()

        // fulfillment.shipping: scheduleFulfillment -> SCHEDULED
        val fulfillment = fulfillmentRepository.findByOrderId(orderId)!!
        assertThat(fulfillment.status).isEqualTo(FulfillmentStatus.SCHEDULED)
        assertThat(fulfillment.customerId).isEqualTo("CUST-006")
        assertThat(fulfillment.scheduledAt).isEqualTo(now)
        assertThat(fulfillment.items.map { "${it.sku} x${it.quantity}" }).containsExactly("SKU-001 x1")

        // notifications.consumer: sendOrderConfirmation -> SENT (A-01), one e-mail to the customer
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer06@example.com")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }
}
