package com.arcadiaeditions

import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.domain.StockReleaseReason
import com.arcadiaeditions.catalog.inventory.domain.events.StockReleased
import com.arcadiaeditions.catalog.inventory.domain.events.StockReleasedItem
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
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus
import com.arcadiaeditions.orders.checkout.core.domain.events.OrderCancelled
import com.arcadiaeditions.orders.checkout.core.domain.events.OrderConfirmationRejected
import com.arcadiaeditions.orders.checkout.core.domain.events.OrderConfirmed
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.domain.PaymentStatus
import com.arcadiaeditions.payments.processing.domain.event.PaymentAuthorized
import com.arcadiaeditions.payments.processing.domain.event.PaymentVoided
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Predicate
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.modulith.events.core.EventPublicationRepository
import org.springframework.modulith.test.EnableScenarios
import org.springframework.modulith.test.Scenario
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager

/**
 * place-order-flow, late authorization (place-order-flow.zfl:148-176 confirmOrder / confirmStockReservation, 236-266
 * voidPayment / releaseStock, 299-309 cancelOrder): a PaymentAuthorized that arrives after the ReservationExpired timer
 * released the stock. In models 0.10.0 the timer does not release while an authorization is in flight (T-52, T-67), so
 * the late authorization comes from a late OrderCreated delivery to payments (data.sql A-09): the fixtures C-22 and
 * C-23 hold the state at the moment PaymentAuthorized is delivered, and the test delivers it (a direct publication, as
 * the outbox would).
 *
 * - C-22 (`order-cancelled`): the order is already CANCELLED: confirmOrder rejects (ORDER_CANCELLED) without calling
 *   inventory, voidPayment voids the hold (AUTHORIZED -> VOIDED), releaseStock is a no-op on the RELEASED stock (no
 *   StockReleased), so cancelOrder never runs again: the order stays cancelled once, by the timer.
 * - C-23 (`stock-not-held`): the order is still CREATED (the timer's StockReleased not handled yet): confirmStockReservation
 *   answers NOT_HELD, confirmOrder rejects (STOCK_NOT_HELD) and the order stays CREATED, the hold is voided, releaseStock
 *   is a no-op. The timer's pending StockReleased then cancels the order once (RESERVATION_EXPIRED, one e-mail); its
 *   redelivery is a no-op.
 *
 * No fake hook is set: pm_card_visa voids are approved by FakePaymentProcessor. Not @Transactional: the module listeners
 * run after commit.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // T-43: no replay, at startup, of incomplete publications another context or run left in event_publication
    properties = ["spring.modulith.events.republish-outstanding-events-on-restart=false"],
)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
@EnableScenarios
@Import(TestClockConfiguration::class)
// T-43: a fresh context per flow test (schema + data.sql reloaded, new async threads). Scenario captures events through
// an InheritableThreadLocal, which async listener threads already created in a reused context never see.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// T-43: no resubmitIncompletePublications tick: its first run, right after startup, resubmits this test's in-flight
// publications and the listener runs twice (seen: two STOCK_UNAVAILABLE notifications for one order)
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowLateAuthorizationIntegrationTest {

    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var publicationRepository: EventPublicationRepository
    @Autowired lateinit var publisher: ApplicationEventPublisher
    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private lateinit var events: RetryLoopEvents

    private val timeout: Duration = Duration.ofSeconds(30)

    private object Targets {
        /** confirmOrder (orders.checkout) */
        const val CONFIRM_ORDER = "orders.checkout.adapters.events.processing.PaymentsProcessingEventsListener.onPaymentAuthorized"
        /** voidPayment (payments.processing) */
        const val VOID_PAYMENT = "OrdersCheckoutEventsListener.onOrderConfirmationRejected"
        /** releaseStock (catalog.inventory) */
        const val RELEASE_ON_VOIDED = "catalog.inventory.events.processing.PaymentsProcessingEventsListener.onPaymentVoided"
        /** cancelOrder (orders.checkout) */
        const val CANCEL_ORDER = "CatalogInventoryEventsListener.onStockReleased"
    }

    @BeforeEach
    fun prepare() {
        clock.reset()
        events = RetryLoopEvents(publicationRepository, publisher, transactionManager)
    }

    @AfterEach
    fun resetClock() {
        clock.reset()
    }

    /** C-22: PaymentAuthorized as authorizePayment emitted it, after the timer released the stock and cancelled the order. */
    private fun c22PaymentAuthorized() =
        PaymentAuthorized("ORD-022", "CUST-022", BigDecimal("49.99"), "EUR", "PSP-AUTH-022", Instant.parse("2026-10-01T09:52:00Z"))

    /** C-23: PaymentAuthorized as authorizePayment emitted it, after the timer released the stock. */
    private fun c23PaymentAuthorized() =
        PaymentAuthorized("ORD-023", "CUST-023", BigDecimal("49.99"), "EUR", "PSP-AUTH-023", Instant.parse("2026-10-01T11:58:30Z"))

    /** C-23: the StockReleased the timer emitted at 11:58:10 (RES-023), whose cancelOrder has not run yet. */
    private fun c23TimerStockReleased() =
        StockReleased(
            "ORD-023",
            StockReleaseReason.RESERVATION_EXPIRED,
            Instant.parse("2026-10-01T11:58:10Z"),
            mutableListOf(StockReleasedItem("RES-023", "SKU-001", 1 as Integer)),
        )

    @Test
    fun lateAuthorizationForCancelledOrderIsRejectedAndVoidedAndTheOrderStaysCancelledOnce(scenario: Scenario) {
        val orderId = "ORD-022"
        val now = TestClock.NOW
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.AUTHORIZED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        val mark = events.mark()

        scenario
            .publish(c22PaymentAuthorized())
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(PaymentVoided::class.java)
            .matching(Predicate { it.orderId == orderId })
            .toArriveAndVerify(
                Consumer { event: PaymentVoided ->
                    assertThat(event.customerId).isEqualTo("CUST-022")
                    assertThat(event.providerReference).isEqualTo("PSP-AUTH-022")
                    assertThat(event.voidedAt).isEqualTo(now)
                }
            )
        awaitCompleted(mark, PaymentVoided::class.java, Targets.RELEASE_ON_VOIDED) { it.orderId == orderId }

        // orders.checkout: confirmOrder on CANCELLED -> exactly one OrderConfirmationRejected ORDER_CANCELLED, no OrderConfirmed
        val rejected = events.since(mark, OrderConfirmationRejected::class.java, Targets.VOID_PAYMENT) { it.orderId == orderId }
        assertThat(rejected).hasSize(1)
        assertThat(rejected.single().reason).isEqualTo("ORDER_CANCELLED")
        assertThat(rejected.single().customerId).isEqualTo("CUST-022")
        assertThat(rejected.single().rejectedAt).isEqualTo(now)
        assertThat(events.since(mark, OrderConfirmed::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()

        // payments.processing: voidPayment from AUTHORIZED -> VOIDED, exactly one PaymentVoided
        assertThat(events.since(mark, PaymentVoided::class.java, Targets.RELEASE_ON_VOIDED) { it.orderId == orderId }).hasSize(1)
        assertVoided(orderId, "ORDER_CANCELLED", Instant.parse("2026-10-01T09:52:00Z"))

        // catalog.inventory: releaseStock is a no-op on the RELEASED stock: no StockReleased, nothing changed
        assertThat(events.since(mark, StockReleased::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertReservationUnchanged(orderId, reservation.version, Instant.parse("2026-10-01T09:50:10Z"))
        assertPositionUnchanged(position.version, position.availableQuantity, position.reservedQuantity, position.soldQuantity)

        // orders.checkout: cancelOrder never runs again; the order stays as the timer cancelled it, once
        assertThat(events.since(mark, OrderCancelled::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        val after = orderRepository.findByOrderId(orderId)!!
        assertThat(after.version).isEqualTo(order.version)
        assertThat(after.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(after.cancelledAt).isEqualTo(Instant.parse("2026-10-01T09:50:20Z"))
        assertThat(after.cancellationReason).isEqualTo("RESERVATION_EXPIRED")
        assertThat(after.confirmedAt).isNull()

        // fulfillment.shipping and notifications.consumer: nothing for this order
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)).isNull()
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).isEmpty()
    }

    @Test
    fun lateAuthorizationAfterStockReleasedIsRejectedAndVoidedAndTheOrderIsCancelledOnce(scenario: Scenario) {
        val orderId = "ORD-023"
        val now = TestClock.NOW
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CREATED)
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.AUTHORIZED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        val mark = events.mark()

        scenario
            .publish(c23PaymentAuthorized())
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(PaymentVoided::class.java)
            .matching(Predicate { it.orderId == orderId })
            .toArriveAndVerify(
                Consumer { event: PaymentVoided ->
                    assertThat(event.customerId).isEqualTo("CUST-023")
                    assertThat(event.providerReference).isEqualTo("PSP-AUTH-023")
                    assertThat(event.voidedAt).isEqualTo(now)
                }
            )
        awaitCompleted(mark, PaymentVoided::class.java, Targets.RELEASE_ON_VOIDED) { it.orderId == orderId }

        // orders.checkout: confirmStockReservation NOT_HELD -> exactly one OrderConfirmationRejected STOCK_NOT_HELD;
        // the order stays CREATED, not confirmed
        val rejected = events.since(mark, OrderConfirmationRejected::class.java, Targets.VOID_PAYMENT) { it.orderId == orderId }
        assertThat(rejected).hasSize(1)
        assertThat(rejected.single().reason).isEqualTo("STOCK_NOT_HELD")
        assertThat(rejected.single().customerId).isEqualTo("CUST-023")
        assertThat(rejected.single().rejectedAt).isEqualTo(now)
        assertThat(events.since(mark, OrderConfirmed::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        val rejectedOrder = orderRepository.findByOrderId(orderId)!!
        assertThat(rejectedOrder.version).isEqualTo(order.version)
        assertThat(rejectedOrder.status).isEqualTo(OrderStatus.CREATED)
        assertThat(rejectedOrder.confirmedAt).isNull()
        assertThat(rejectedOrder.cancelledAt).isNull()

        // payments.processing: voidPayment from AUTHORIZED -> VOIDED, exactly one PaymentVoided
        assertThat(events.since(mark, PaymentVoided::class.java, Targets.RELEASE_ON_VOIDED) { it.orderId == orderId }).hasSize(1)
        assertVoided(orderId, "STOCK_NOT_HELD", Instant.parse("2026-10-01T11:58:30Z"))

        // catalog.inventory: confirmStockReservation NOT_HELD and releaseStock change nothing: no StockReleased of their own
        assertThat(events.since(mark, StockReleased::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertThat(events.since(mark, OrderCancelled::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertReservationUnchanged(orderId, reservation.version, Instant.parse("2026-10-01T11:58:10Z"))
        assertPositionUnchanged(position.version, position.availableQuantity, position.reservedQuantity, position.soldQuantity)

        // the timer's StockReleased is handled now: cancelOrder cancels the order (RESERVATION_EXPIRED), once
        val timerReleased = events.mark()
        scenario
            .publish(c23TimerStockReleased())
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderCancelledNotificationSent::class.java)
            .matching(Predicate { it.orderId == orderId })
            .toArriveAndVerify(
                Consumer { event: OrderCancelledNotificationSent ->
                    assertThat(event.customerId).isEqualTo("CUST-023")
                    assertThat(event.reason).isEqualTo("RESERVATION_EXPIRED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer23@example.com")
                    assertThat(event.sentAt).isEqualTo(now)
                }
            )
        val cancelled = events.since(timerReleased, OrderCancelled::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }
        assertThat(cancelled.distinct()).hasSize(1)
        assertThat(cancelled.first().reason).isEqualTo("RESERVATION_EXPIRED")
        val cancelledOrder = orderRepository.findByOrderId(orderId)!!
        assertCancelledOnce(orderId, now)

        // redelivery of the timer's StockReleased: cancelOrder is a no-op, no OrderCancelled, nothing changed
        val redelivered = events.mark()
        events.redeliver(c23TimerStockReleased(), timeout)
        assertThat(events.since(redelivered, OrderCancelled::class.java, RetryLoopTargets.ANY) { it.orderId == orderId }).isEmpty()
        assertThat(orderRepository.findByOrderId(orderId)!!.version).isEqualTo(cancelledOrder.version)
        assertCancelledOnce(orderId, now)
        assertReservationUnchanged(orderId, reservation.version, Instant.parse("2026-10-01T11:58:10Z"))
        assertPositionUnchanged(position.version, position.availableQuantity, position.reservedQuantity, position.soldQuantity)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.VOIDED)
    }

    /** Waits until the publication of [type] to the listener [target] (published after [mark]) is completed. */
    private fun <T : Any> awaitCompleted(mark: Set<UUID>, type: Class<T>, target: String, matching: (T) -> Boolean) {
        await().atMost(timeout).until {
            val ofType = { p: org.springframework.modulith.events.core.TargetEventPublication ->
                p.identifier !in mark &&
                    type.isInstance(p.event) &&
                    p.targetIdentifier.value.contains(target) &&
                    matching(type.cast(p.event))
            }
            publicationRepository.findIncompletePublications().none(ofType) &&
                publicationRepository.findCompletedPublications().any(ofType)
        }
    }

    /** payments.processing: the late hold, AUTHORIZED in the fixture, voided once, never captured. */
    private fun assertVoided(orderId: String, reason: String, authorizedAt: Instant) {
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.VOIDED)
        assertThat(payment.attemptCount).isEqualTo(1)
        assertThat(payment.providerReference).isEqualTo("PSP-AUTH-" + orderId.removePrefix("ORD-"))
        assertThat(payment.authorizedAt).isEqualTo(authorizedAt)
        assertThat(payment.voidedAt).isEqualTo(TestClock.NOW)
        assertThat(payment.voidFailedAt).isNull()
        assertThat(payment.capturedAt).isNull()
        assertThat(payment.failureReason).isEqualTo(reason)
    }

    /** catalog.inventory: the reservation the timer released, not saved again. */
    private fun assertReservationUnchanged(orderId: String, version: Int?, releasedAt: Instant) {
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.version).isEqualTo(version)
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.sku).isEqualTo("SKU-001")
        assertThat(reservation.quantity).isEqualTo(1)
        assertThat(reservation.releasedAt).isEqualTo(releasedAt)
        assertThat(reservation.releaseReason).isEqualTo(StockReleaseReason.RESERVATION_EXPIRED)
        assertThat(reservation.confirmedAt).isNull()
    }

    /** catalog.inventory: SKU-001 counters untouched (the released unit went back to available with the timer). */
    private fun assertPositionUnchanged(version: Int?, available: Integer?, reserved: Integer?, sold: Integer?) {
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(position.version).isEqualTo(version)
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity))
            .containsExactly(available, reserved, sold)
    }

    /** orders.checkout and notifications.consumer: CANCELLED by the timer's StockReleased, one notification, one e-mail. */
    private fun assertCancelledOnce(orderId: String, now: Instant) {
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)).isNotNull()
        }
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.customerId).isEqualTo("CUST-023")
        assertThat(order.createdAt).isEqualTo(Instant.parse("2026-10-01T11:48:00Z"))
        assertThat(order.cancelledAt).isEqualTo(now)
        assertThat(order.cancellationReason).isEqualTo("RESERVATION_EXPIRED")
        assertThat(order.confirmedAt).isNull()
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer23@example.com")
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }
}
