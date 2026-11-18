package com.arcadiaeditions

import com.arcadiaeditions.RetryLoopRecorder.Authorize
import com.arcadiaeditions.RetryLoopRecorder.Backoff
import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.domain.StockReleaseReason
import com.arcadiaeditions.catalog.inventory.domain.events.StockReleased
import com.arcadiaeditions.catalog.inventory.events.scheduler.ReservationExpiredJob
import com.arcadiaeditions.catalog.inventory.repository.jpa.InventoryPositionRepository
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.catalog.inventory.service.InventoryService
import com.arcadiaeditions.catalog.inventory.service.dtos.ExpireReservationInput
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
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.OrderItemInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.ShippingAddressInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.StartOrderCheckoutInput
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository
import com.arcadiaeditions.payments.processing.adapter.driven.processor.FakePaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.application.port.driving.PaymentsProcessingService
import com.arcadiaeditions.payments.processing.application.port.driving.dto.GetPaymentStatusInput
import com.arcadiaeditions.payments.processing.domain.GetPaymentStatusOutcome
import com.arcadiaeditions.payments.processing.domain.PaymentStatus
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetryExhausted
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.function.BiConsumer
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
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
 * place-order-flow, ReservationExpired (place-order-flow.zfl:42-69 header, 268-309): ten minutes after OrderCreated the
 * timer runs expireReservation, which asks getPaymentStatus and releases the RESERVED lines (RESERVATION_EXPIRED) only on
 * NoPaymentResult; on PaymentStartedResult payment owns the end. ReservationExpiredJob is off in test contexts (T-38):
 * each test builds it on the context's beans and runs it.
 *
 * - [noPaymentStartedEndsInOrderCancelledNotificationSentWithReservationExpired]: C-30 (ORD-030, RES-030 past its
 *   deadline, no Payment) at the test "now": released, OrderCancelled, OrderCancelledNotificationSent with
 *   RESERVATION_EXPIRED. The same run keeps C-14 (payment started). A second run, and expireReservation on C-11 (already
 *   RELEASED, a late Payment AUTHORIZATION_REQUESTED, A-09), are no-ops.
 * - [paymentStartedTimerReleasesNothingAndPaymentEndsTheOrder]: the race skipped at baseline 1 (P-06 B). A checkout of
 *   CUST-011 (C-11 input) with pm_card_processorNoAnswer: AUTHORIZATION_REQUESTED is committed before the processor call
 *   (T-52); with that first call still in flight the Clock passes the 10 minutes and the timer runs: STARTED, nothing
 *   released. Each call is abandoned after the processor timeout (shortened here to [PROCESSOR_TIMEOUT]) and fails with
 *   processor_timeout; 3 attempts (backoff recorded, not slept, [RetryLoopTestConfiguration]) end in
 *   PaymentRetryExhausted, releaseStock PAYMENT_RETRY_EXHAUSTED and OrderCancelledNotificationSent. The stalled calls
 *   end only by that timeout (the executor interrupts them): the test never calls releaseStalledAuthorizations, whose
 *   throw would leave AUTHORIZATION_REQUESTED stuck (T-75, P-10).
 *
 * Not @Transactional: the module listeners run after commit.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties =
        [
            // T-43: no replay, at startup, of incomplete publications another context or run left in event_publication
            "spring.modulith.events.republish-outstanding-events-on-restart=false",
            // T-52: each stalled processor call is abandoned after this, as FAILED processor_timeout (ZFL: 30 s)
            "arcadia.payments.processor-timeout=" + PlaceOrderFlowReservationExpiredIntegrationTest.PROCESSOR_TIMEOUT,
        ],
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
class PlaceOrderFlowReservationExpiredIntegrationTest {

    companion object {
        /** Long enough for the timer to run inside the first call, short enough for 3 attempts in a test. */
        const val PROCESSOR_TIMEOUT = "5s"
        const val TIMER_TARGET = "CatalogInventoryEventsListener.onStockReleased"
    }

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var inventoryService: InventoryService
    @Autowired lateinit var paymentsProcessingService: PaymentsProcessingService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var paymentProcessor: FakePaymentProcessor
    @Autowired lateinit var recorder: RetryLoopRecorder
    @Autowired lateinit var events: RetryLoopEvents
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    /** 3 processor timeouts, plus the listeners. */
    private val paymentEndTimeout: Duration = Duration.ofSeconds(90)

    /** A-04: reservation window of 10 minutes; the timer runs once the Clock is past it. */
    private val pastReservationWindow: Duration = Duration.ofMinutes(10).plusSeconds(1)

    @BeforeEach
    fun reset() {
        clock.reset()
        recorder.reset()
    }

    @AfterEach
    fun waitForStalledCalls() {
        // no releaseStalledAuthorizations (its throw leaves AUTHORIZATION_REQUESTED stuck, T-75): a stalled call ends by
        // the processor timeout, which interrupts it; wait for that, so it ends in this test's context
        await().atMost(paymentEndTimeout).until { paymentProcessor.stalledOrderIds.isEmpty() }
        clock.reset()
        recorder.reset()
    }

    private fun job() = ReservationExpiredJob(stockReservationRepository, inventoryService, clock)

    /** C-30: no Payment ever started; the timer at the test "now" releases RES-030 and the order ends cancelled. */
    @Test
    fun noPaymentStartedEndsInOrderCancelledNotificationSentWithReservationExpired(scenario: Scenario) {
        val orderId = "ORD-030"
        val now = TestClock.NOW
        assertThat(paymentRepository.findByOrderId(orderId)).isNull()
        assertThat(paymentsProcessingService.getPaymentStatus(GetPaymentStatusInput(orderId)).outcome)
            .isEqualTo(GetPaymentStatusOutcome.NO_PAYMENT)
        // due at the test "now": RES-030 (11:50, no Payment) and RES-014 (11:55, Payment FAILED attempt 2)
        assertThat(
                stockReservationRepository.findByStatusAndExpiresAtLessThanEqual(ReservationStatus.RESERVED, now).map {
                    it.orderId
                }
            )
            .containsExactlyInAnyOrder("ORD-030", "ORD-014")
        val c14Payment = paymentRepository.findByOrderId("ORD-014")!!.snapshot()
        val before = inventoryPositionRepository.findBySku("SKU-006")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }
        val mark = events.mark()

        scenario
            .stimulate(Supplier { job().releaseExpiredReservations() })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderCancelledNotificationSent::class.java)
            .matching { it.orderId == orderId }
            .toArriveAndVerify(
                BiConsumer { event: OrderCancelledNotificationSent, _: Unit ->
                    assertThat(event.customerId).isEqualTo("CUST-030")
                    assertThat(event.reason).isEqualTo("RESERVATION_EXPIRED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer30@example.com")
                    assertThat(event.sentAt).isEqualTo(now)
                }
            )

        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)).isNotNull()
        }
        // orders.checkout: cancelOrder -> CANCELLED (A-07)
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.cancelledAt).isEqualTo(now)
        assertThat(order.cancellationReason).isEqualTo("RESERVATION_EXPIRED")
        assertThat(order.confirmedAt).isNull()
        // catalog.inventory: expireReservation on NO_PAYMENT -> RELEASED (A-02, A-04), one StockReleased
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.reservationId).isEqualTo("RES-030")
        assertThat(reservation.releasedAt).isEqualTo(now)
        assertThat(reservation.releaseReason).isEqualTo(StockReleaseReason.RESERVATION_EXPIRED)
        assertThat(reservation.confirmedAt).isNull()
        val position = inventoryPositionRepository.findBySku("SKU-006")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available + 1, reserved - 1, sold)
        assertThat(events.since(mark, StockReleased::class.java, TIMER_TARGET) { it.orderId == orderId })
            .extracting<StockReleaseReason?> { it.reason }
            .containsExactly(StockReleaseReason.RESERVATION_EXPIRED)
        // payments.processing: getPaymentStatus is read only, no Payment created
        assertThat(paymentRepository.findByOrderId(orderId)).isNull()
        // fulfillment.shipping: nothing; notifications.consumer: one e-mail, none for confirmation
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.recipient).isEqualTo("customer30@example.com")
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)

        // C-14: past its deadline in the same run, payment started: nothing released, payment untouched
        assertC14Kept(c14Payment)

        // a second run (C-30 already RELEASED, C-14 started) and expireReservation on C-11 (RELEASED, Payment
        // AUTHORIZATION_REQUESTED came later, A-09): no-ops, no event
        val c11Reservation = stockReservationRepository.findByOrderId("ORD-011").single()
        assertThat(c11Reservation.status).isEqualTo(ReservationStatus.RELEASED)
        val redelivered = events.mark()
        job().releaseExpiredReservations()
        inventoryService.expireReservation(ExpireReservationInput("ORD-011"))
        assertThat(events.since(redelivered, StockReleased::class.java, RetryLoopTargets.ANY)).isEmpty()
        assertThat(stockReservationRepository.findByOrderId("ORD-011").single().version).isEqualTo(c11Reservation.version)
        assertThat(inventoryPositionRepository.findBySku("SKU-006")!!.availableQuantity?.toInt()).isEqualTo(available + 1)
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
        assertC14Kept(c14Payment)
    }

    /** P-06 B: the timer fires while the first authorization is in flight; it releases nothing, payment ends the order. */
    @Test
    fun paymentStartedTimerReleasesNothingAndPaymentEndsTheOrder() {
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }
        val c14Payment = paymentRepository.findByOrderId("ORD-014")!!.snapshot()
        val mark = events.mark()

        // StartOrderCheckout commits on its own: OrderCreated reaches reserveStock and authorizePayment
        val orderId = ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()).orderId!!
        val createdAt = TestClock.NOW
        await().atMost(timeout).untilAsserted {
            assertThat(stockReservationRepository.findByOrderId(orderId).map { it.status })
                .containsExactly(ReservationStatus.RESERVED)
            assertThat(paymentProcessor.stalledOrderIds).contains(orderId)
        }
        // T-52: AUTHORIZATION_REQUESTED is committed while the first call waits for the processor
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.AUTHORIZATION_REQUESTED)
        assertThat(stockReservationRepository.findByOrderId(orderId).single().expiresAt)
            .isEqualTo(createdAt.plus(Duration.ofMinutes(10)))

        // the Clock passes 10 minutes after OrderCreated; the timer runs with the first call still in flight
        clock.advance(pastReservationWindow)
        val expiredAt = clock.instant()
        assertThat(
                stockReservationRepository.findByStatusAndExpiresAtLessThanEqual(ReservationStatus.RESERVED, expiredAt)
                    .map { it.orderId }
            )
            .contains(orderId, "ORD-014")
        val started = paymentsProcessingService.getPaymentStatus(GetPaymentStatusInput(orderId))
        assertThat(started.outcome).isEqualTo(GetPaymentStatusOutcome.STARTED)
        assertThat(started.started!!.status).isEqualTo(PaymentStatus.AUTHORIZATION_REQUESTED)
        job().releaseExpiredReservations()
        // still inside attempt 1: the call has not timed out yet
        assertThat(paymentProcessor.stalledOrderIds).contains(orderId)
        assertThat(recorder.steps).containsExactly(Authorize(1, PaymentStatus.AUTHORIZATION_REQUESTED, 1))
        // getPaymentStatus STARTED: nothing released, payment owns the end
        assertThat(stockReservationRepository.findByOrderId(orderId).single().status).isEqualTo(ReservationStatus.RESERVED)
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CREATED)

        // payment ends the order: 3 attempts, each abandoned after the processor timeout, then retry-exhausted
        await().atMost(paymentEndTimeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)).isNotNull()
        }
        assertThat(recorder.steps)
            .containsExactly(
                Authorize(1, PaymentStatus.AUTHORIZATION_REQUESTED, 1),
                Backoff(Duration.ofSeconds(5), PaymentStatus.FAILED, 1),
                Authorize(2, PaymentStatus.AUTHORIZATION_REQUESTED, 2),
                Backoff(Duration.ofSeconds(15), PaymentStatus.FAILED, 2),
                Authorize(3, PaymentStatus.AUTHORIZATION_REQUESTED, 3),
            )
        assertPaymentEndState(orderId, createdAt, expiredAt, available, reserved, sold, mark)

        // the timer never released this order; a late run finds nothing RESERVED: no-op, no event
        val late = events.mark()
        job().releaseExpiredReservations()
        assertThat(events.since(late, StockReleased::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertThat(orderRepository.findByOrderId(orderId)!!.cancellationReason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
        assertC14Kept(c14Payment)
    }

    /** C-11 input: the checkout of CUST-011, here with the processor that never answers. */
    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-011",
            customerEmail = "customer11@example.com",
            paymentMethodId = FakePaymentProcessor.NO_ANSWER_PAYMENT_METHOD,
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-001", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    private fun assertPaymentEndState(
        orderId: String,
        createdAt: Instant,
        expiredAt: Instant,
        available: Int,
        reserved: Int,
        sold: Int,
        mark: Set<UUID>,
    ) {
        // payments.processing: 3 timeouts (A-06 processor_timeout), retryPayment at 3 -> RETRY_EXHAUSTED (A-03)
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.RETRY_EXHAUSTED)
        assertThat(payment.attemptCount!!.toInt()).isEqualTo(3)
        assertThat(payment.failureReason).isEqualTo("processor_timeout")
        assertThat(payment.failedAt).isEqualTo(expiredAt)
        assertThat(payment.providerReference).isNull()
        assertThat(payment.authorizedAt).isNull()
        assertThat(payment.voidedAt).isNull()
        assertThat(events.since(mark, PaymentRetryExhausted::class.java, RetryLoopTargets.RELEASE_ON_RETRY_EXHAUSTED) {
                it.orderId == orderId
            })
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactly(3)

        // catalog.inventory: released once, by payment (PAYMENT_RETRY_EXHAUSTED), never by the timer
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.reservedAt).isEqualTo(createdAt)
        assertThat(reservation.releasedAt).isEqualTo(expiredAt)
        assertThat(reservation.releaseReason).isEqualTo(StockReleaseReason.PAYMENT_RETRY_EXHAUSTED)
        assertThat(events.since(mark, StockReleased::class.java, TIMER_TARGET) { it.orderId == orderId })
            .extracting<StockReleaseReason?> { it.reason }
            .containsExactly(StockReleaseReason.PAYMENT_RETRY_EXHAUSTED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved, sold)

        // orders.checkout: cancelled once
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.createdAt).isEqualTo(createdAt)
        assertThat(order.cancelledAt).isEqualTo(expiredAt)
        assertThat(order.cancellationReason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
        assertThat(order.confirmedAt).isNull()

        // fulfillment.shipping: nothing; notifications.consumer: one e-mail, none for confirmation
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer11@example.com")
        assertThat(notification.sentAt).isEqualTo(expiredAt)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }

    /** C-14: RES-014 past its deadline (11:55), Payment FAILED attempt 2 (started): the timer releases nothing. */
    private fun assertC14Kept(payment: List<Any?>) {
        assertThat(stockReservationRepository.findByOrderId("ORD-014").single().status).isEqualTo(ReservationStatus.RESERVED)
        assertThat(orderRepository.findByOrderId("ORD-014")!!.status).isEqualTo(OrderStatus.CREATED)
        assertThat(paymentRepository.findByOrderId("ORD-014")!!.snapshot()).isEqualTo(payment)
    }
}
