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
import com.arcadiaeditions.payments.processing.domain.event.PaymentDeclined
import com.arcadiaeditions.payments.processing.domain.event.PaymentFailed
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetried
import java.math.BigDecimal
import java.time.Duration
import java.util.function.BiConsumer
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
 * place-order-flow, path `retry` then declined (place-order-flow.zfl:114-146, 254-266): the first authorization fails
 * (pm_card_processorErrorThenDecline), PaymentFailed -> retryPayment -> PaymentRetried -> reauthorizePayment declines
 * attempt 2 after the 5 s backoff, PaymentDeclined -> releaseStock -> cancelOrder, and the flow ends in
 * OrderCancelledNotificationSent with PAYMENT_DECLINED (shape of C-08 with attemptCount 2). T-59: attempts counted only by
 * authorizePayment (1) and reauthorizePayment (2); a declined attempt is not retried; the backoff is recorded by
 * [RetryLoopTestConfiguration], not slept; the PaymentFailed and PaymentRetried of attempt 1 delivered again change
 * nothing. Input is the C-06 order (CUST-006, SKU-001 x1); stock counters are checked relative to the values read before
 * (T-43). Not @Transactional: the module listeners run after commit.
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
// T-43: a fresh context per flow class (schema + data.sql reloaded, new async threads). Scenario captures events through
// an InheritableThreadLocal, which async listener threads already created in a reused context never see.
@DirtiesContext
// T-43: no resubmitIncompletePublications tick: its first run, right after startup, resubmits this test's in-flight
// publications and the listener runs twice (seen: two STOCK_UNAVAILABLE notifications for one order)
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowRetryDeclinedIntegrationTest {

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
            customerId = "CUST-006",
            customerEmail = "customer06@example.com",
            paymentMethodId = "pm_card_processorErrorThenDecline",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-001", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun processorErrorThenDeclineIsRetriedAndEndsInOrderCancelledNotificationSent(scenario: Scenario) {
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
                    assertThat(event.customerId).isEqualTo("CUST-006")
                    assertThat(event.reason).isEqualTo("PAYMENT_DECLINED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer06@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                    assertEndState(order.orderId!!, available, reserved, sold)
                }
            )

        val id = orderId!!
        assertRetryLoop(id, mark)
        assertStaleRedeliveriesChangeNothing(id, mark, available, reserved, sold)
    }

    /** attempt 1 by authorizePayment, the 5 s backoff while FAILED (attempt 1), attempt 2 by reauthorizePayment declined. */
    private fun assertRetryLoop(orderId: String, mark: Set<java.util.UUID>) {
        assertThat(recorder.steps)
            .containsExactly(
                Authorize(1, PaymentStatus.AUTHORIZATION_REQUESTED, 1),
                Backoff(Duration.ofSeconds(5), PaymentStatus.FAILED, 1),
                Authorize(2, PaymentStatus.AUTHORIZATION_REQUESTED, 2),
            )
        assertThat(events.since(mark, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId })
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(1)
        assertThat(
                events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(1)
        assertThat(
                events.since(mark, PaymentDeclined::class.java, RetryLoopTargets.RELEASE_ON_DECLINED) { it.orderId == orderId }
            )
            .hasSize(1)
    }

    /** The PaymentFailed and PaymentRetried of attempt 1 delivered again after the decline: no-ops, no event. */
    private fun assertStaleRedeliveriesChangeNothing(
        orderId: String,
        mark: Set<java.util.UUID>,
        available: Int,
        reserved: Int,
        sold: Int,
    ) {
        val failed = events.since(mark, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId }.single()
        val retried =
            events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }.single()
        val payment = paymentRepository.findByOrderId(orderId)!!.snapshot()
        val steps = recorder.steps.toList()
        val redelivered = events.mark()

        events.redeliver(failed, timeout)
        events.redeliver(retried, timeout)

        assertThat(paymentRepository.findByOrderId(orderId)!!.snapshot()).isEqualTo(payment)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.DECLINED)
        assertThat(recorder.steps).isEqualTo(steps)
        // only the two redelivered publications: no PaymentRetried, PaymentFailed or PaymentDeclined of their own
        assertThat(events.since(redelivered, PaymentFailed::class.java, RetryLoopTargets.RETRY_PAYMENT) { it.orderId == orderId })
            .containsExactly(failed)
        assertThat(events.since(redelivered, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId })
            .containsExactly(retried)
        assertThat(events.since(redelivered, PaymentDeclined::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        // the order stays cancelled once, the stock released once
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved, sold)
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
        assertThat(order.customerId).isEqualTo("CUST-006")
        assertThat(order.createdAt).isEqualTo(now)
        assertThat(order.cancelledAt).isEqualTo(now)
        assertThat(order.cancellationReason).isEqualTo("PAYMENT_DECLINED")
        assertThat(order.confirmedAt).isNull()

        // catalog.inventory: reserveStock -> RESERVED, releaseStock -> RELEASED, counters back (A-02)
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.sku).isEqualTo("SKU-001")
        assertThat(reservation.quantity).isEqualTo(1)
        assertThat(reservation.reservedAt).isEqualTo(now)
        assertThat(reservation.releasedAt).isEqualTo(now)
        assertThat(reservation.releaseReason.toString()).isEqualTo("PAYMENT_DECLINED")
        assertThat(reservation.confirmedAt).isNull()
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved, sold)

        // payments.processing: authorizePayment fails (attempt 1), retryPayment, reauthorizePayment -> DECLINED (attempt 2, A-01, A-06)
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.DECLINED)
        assertThat(payment.attemptCount).isEqualTo(2)
        assertThat(payment.declinedAt).isEqualTo(now)
        assertThat(payment.failureReason).isEqualTo("card_declined")
        assertThat(payment.providerReference).isNull()
        assertThat(payment.authorizedAt).isNull()
        assertThat(payment.capturedAt).isNull()
        assertThat(payment.failedAt).isNull()
        assertThat(payment.voidedAt).isNull()

        // fulfillment.shipping: nothing scheduled
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()

        // notifications.consumer: sendOrderCancelledNotification -> SENT, one e-mail, none for confirmation
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer06@example.com")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }
}
