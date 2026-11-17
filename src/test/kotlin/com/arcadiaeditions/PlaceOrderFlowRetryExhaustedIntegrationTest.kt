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
import com.arcadiaeditions.notifications.consumer.FakeEmailSender
import com.arcadiaeditions.notifications.consumer.domain.NotificationChannel
import com.arcadiaeditions.notifications.consumer.domain.NotificationStatus
import com.arcadiaeditions.notifications.consumer.domain.NotificationType
import com.arcadiaeditions.notifications.consumer.domain.events.OrderCancelledNotificationSent
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
import com.arcadiaeditions.payments.processing.domain.event.PaymentFailed
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetried
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetryExhausted
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
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
 * place-order-flow, path `orderCancelled` by retries exhausted (place-order-flow.zfl:114-146, 254-266): StartOrderCheckout
 * with pm_card_processorError (every attempt fails, 3 attempts, A-03) ends in OrderCancelledNotificationSent, stock back
 * to available. End state is the shape of C-09 (CUST-009); the input line SKU-001 x1 49.99 is the one of C-13 (C-09 holds
 * SKU-002). T-59: attempts counted only by authorizePayment (1) and reauthorizePayment (2, 3); the backoff is 5 s before
 * attempt 2 and 15 s before attempt 3, each asked while the Payment is FAILED ([RetryLoopTestConfiguration] records it,
 * no sleep); at attempt 3 retryPayment emits PaymentRetryExhausted; every PaymentFailed and PaymentRetried of the loop
 * delivered again changes nothing. Second test: the C-13 fixture (ORD-013, Payment FAILED at attempt 3) ignores a stale
 * PaymentFailed (attempt 2) and is exhausted by its pending one (attempt 3). Not @Transactional: the module listeners run
 * after commit.
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
class PlaceOrderFlowRetryExhaustedIntegrationTest {

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

    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-009",
            customerEmail = "customer09@example.com",
            paymentMethodId = "pm_card_processorError",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-001", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun retriesExhaustedEndsInOrderCancelledNotificationSent(scenario: Scenario) {
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val available = before.availableQuantity!!.toInt()
        val reserved = before.reservedQuantity!!.toInt()
        val sold = before.soldQuantity!!.toInt()
        val mark = events.mark()
        var orderId: String? = null

        scenario
            .stimulate(Supplier { ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()) })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderCancelledNotificationSent::class.java)
            .toArriveAndVerify(
                BiConsumer { event: OrderCancelledNotificationSent, order: Order ->
                    orderId = order.orderId
                    assertThat(event.orderId).isEqualTo(order.orderId)
                    assertThat(event.customerId).isEqualTo("CUST-009")
                    assertThat(event.reason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer09@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                    assertEndState(order.orderId!!, available, reserved, sold)
                }
            )

        val id = orderId!!
        assertRetryLoop(id, mark)
        assertStaleRedeliveriesChangeNothing(id, mark, available, reserved, sold)
    }

    /** attempt 1 by authorizePayment; 5 s, attempt 2; 15 s, attempt 3 by reauthorizePayment; then retry-exhausted. */
    private fun assertRetryLoop(orderId: String, mark: Set<UUID>) {
        assertThat(recorder.steps)
            .containsExactly(
                Authorize(1, PaymentStatus.AUTHORIZATION_REQUESTED, 1),
                Backoff(Duration.ofSeconds(5), PaymentStatus.FAILED, 1),
                Authorize(2, PaymentStatus.AUTHORIZATION_REQUESTED, 2),
                Backoff(Duration.ofSeconds(15), PaymentStatus.FAILED, 2),
                Authorize(3, PaymentStatus.AUTHORIZATION_REQUESTED, 3),
            )
        // retryPayment never increments: each PaymentRetried carries the attemptCount of the PaymentFailed it answers
        assertThat(events.since(mark, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId })
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactlyInAnyOrder(1, 2, 3)
        assertThat(
                events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactlyInAnyOrder(1, 2)
        assertThat(
                events.since(mark, PaymentRetryExhausted::class.java, RetryLoopTargets.RELEASE_ON_RETRY_EXHAUSTED) {
                    it.orderId == orderId
                }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(3)
    }

    /** Every PaymentFailed (1, 2, 3) and PaymentRetried (1, 2) of the loop delivered again: no-ops, no event. */
    private fun assertStaleRedeliveriesChangeNothing(
        orderId: String,
        mark: Set<UUID>,
        available: Int,
        reserved: Int,
        sold: Int,
    ) {
        val failed = events.since(mark, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId }
        val retried =
            events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }
        val payment = paymentRepository.findByOrderId(orderId)!!.snapshot()
        val steps = recorder.steps.toList()
        val redelivered = events.mark()

        (failed + retried).forEach { events.redeliver(it, timeout) }

        assertThat(paymentRepository.findByOrderId(orderId)!!.snapshot()).isEqualTo(payment)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.RETRY_EXHAUSTED)
        assertThat(recorder.steps).isEqualTo(steps)
        // only the redelivered publications: no PaymentRetried, PaymentFailed or PaymentRetryExhausted of their own
        assertThat(events.since(redelivered, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId })
            .containsExactlyInAnyOrderElementsOf(failed)
        assertThat(
                events.since(redelivered, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) {
                    it.orderId == orderId
                }
            )
            .containsExactlyInAnyOrderElementsOf(retried)
        assertThat(events.since(redelivered, PaymentRetryExhausted::class.java, RetryLoopTargets.ANY) { it.orderId == orderId })
            .isEmpty()
        // the order stays cancelled once, the stock released once
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved, sold)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }

    /**
     * C-13: ORD-013 is CREATED, RES-013 RESERVED, Payment FAILED at attempt 3 (the limit) with PaymentFailed pending. A
     * stale PaymentFailed (attempt 2) is a no-op; the pending one (attempt 3) is retry-exhausted with no backoff and no
     * processor call, and the flow ends in OrderCancelledNotificationSent (PAYMENT_RETRY_EXHAUSTED) for ORD-013.
     */
    @Test
    fun c13PendingPaymentFailedAtTheLimitEndsInOrderCancelledNotificationSent(scenario: Scenario) {
        val orderId = "ORD-013"
        recorder.orderId = orderId
        val fixture = paymentRepository.findByOrderId(orderId)!!
        assertThat(fixture.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(fixture.attemptCount!!.toInt()).isEqualTo(3)
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }
        val mark = events.mark()

        // stale: attempt 2 already failed and was retried
        events.redeliver(
            PaymentFailed(orderId, "CUST-013", 2 as Integer, "processor_timeout", Instant.parse("2026-10-01T11:57:15Z")),
            timeout,
        )
        assertThat(paymentRepository.findByOrderId(orderId)!!.snapshot()).isEqualTo(fixture.snapshot())
        assertThat(events.since(mark, PaymentRetried::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertThat(events.since(mark, PaymentRetryExhausted::class.java, RetryLoopTargets.ANY) { it.orderId == orderId })
            .isEmpty()

        scenario
            .publish(PaymentFailed(orderId, "CUST-013", 3 as Integer, "processor_timeout", Instant.parse("2026-10-01T11:57:30Z")))
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderCancelledNotificationSent::class.java)
            .matching(Predicate { it.orderId == orderId })
            .toArriveAndVerify(
                Consumer { event: OrderCancelledNotificationSent ->
                    assertThat(event.customerId).isEqualTo("CUST-013")
                    assertThat(event.reason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
                    assertThat(event.recipient).isEqualTo("customer13@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                }
            )

        // no backoff, no processor call: the limit is reached
        assertThat(recorder.steps).isEmpty()
        assertThat(events.since(mark, PaymentRetried::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertThat(
                events.since(mark, PaymentRetryExhausted::class.java, RetryLoopTargets.RELEASE_ON_RETRY_EXHAUSTED) {
                    it.orderId == orderId
                }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(3)
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)).isNotNull()
        }
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.RETRY_EXHAUSTED)
        assertThat(payment.attemptCount!!.toInt()).isEqualTo(3)
        assertThat(payment.failedAt).isEqualTo(fixture.failedAt)
        assertThat(payment.failureReason).isEqualTo("processor_timeout")
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.cancellationReason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.releaseReason.toString()).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available + 1, reserved - 1, sold)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }

    private fun assertEndState(orderId: String, available: Int, reserved: Int, sold: Int) {
        val now = TestClock.NOW
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED))
                .isNotNull()
        }

        // orders.checkout: startOrderCheckout -> CREATED, cancelOrder -> CANCELLED (A-07)
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.customerId).isEqualTo("CUST-009")
        assertThat(order.createdAt).isEqualTo(now)
        assertThat(order.cancelledAt).isEqualTo(now)
        assertThat(order.cancellationReason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
        assertThat(order.confirmedAt).isNull()

        // catalog.inventory: reserveStock -> RESERVED, releaseStock -> RELEASED, counters back (A-02)
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.sku).isEqualTo("SKU-001")
        assertThat(reservation.quantity).isEqualTo(1)
        assertThat(reservation.reservedAt).isEqualTo(now)
        assertThat(reservation.releasedAt).isEqualTo(now)
        assertThat(reservation.releaseReason.toString()).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
        assertThat(reservation.confirmedAt).isNull()
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved, sold)

        // payments.processing: authorizePayment fails (1), reauthorizePayment fails (2, 3), retryPayment at 3 ->
        // RETRY_EXHAUSTED (A-01, A-03)
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.RETRY_EXHAUSTED)
        assertThat(payment.attemptCount).isEqualTo(3)
        assertThat(payment.failedAt).isEqualTo(now)
        assertThat(payment.declinedAt).isNull()
        assertThat(payment.failureReason).isEqualTo("processor_timeout")
        assertThat(payment.providerReference).isNull()
        assertThat(payment.authorizedAt).isNull()
        assertThat(payment.capturedAt).isNull()
        assertThat(payment.voidedAt).isNull()

        // fulfillment.shipping: nothing scheduled
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()

        // notifications.consumer: sendOrderCancelledNotification -> SENT, one e-mail, none for confirmation
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer09@example.com")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }
}
