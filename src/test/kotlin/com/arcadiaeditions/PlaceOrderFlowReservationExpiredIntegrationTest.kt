package com.arcadiaeditions

import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.events.scheduler.ReservationExpiredJob
import com.arcadiaeditions.catalog.inventory.repository.jpa.InventoryPositionRepository
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.catalog.inventory.service.InventoryService
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
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.test.EnableScenarios
import org.springframework.modulith.test.Scenario
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.support.TransactionTemplate

/**
 * place-order-flow, path `orderCancelled` by reservation expired (place-order-flow.zfl:230-260, end 262-266):
 * StartOrderCheckout with pm_card_processorNoAnswer (the processor never answers, payment never resolves); once the
 * Clock passes 10 minutes after OrderCreated (A-04) the ReservationExpired timer releases the stock and the order ends
 * in OrderCancelledNotificationSent with RESERVATION_EXPIRED. End state is the shape of C-11 (CUST-011, SKU-001 x1),
 * except the Payment: the stalled authorization is never committed, so no Payment row is read back. The same timer run
 * also releases C-14 (ORD-014, past its deadline at the test "now").
 *
 * ReservationExpiredJob is off in test contexts (T-38): the test builds it on the context's beans and runs it. Payment
 * never resolves, so the P-06 race (PaymentAuthorized after the release) cannot happen here. Not @Transactional: the
 * module listeners run after commit.
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
// T-43: a fresh context per flow class (schema + data.sql reloaded, new async threads). Scenario captures events through
// an InheritableThreadLocal, which async listener threads already created in a reused context never see.
@DirtiesContext
// T-43: no resubmitIncompletePublications tick: its first run, right after startup, resubmits this test's in-flight
// publications and the listener runs twice (seen: two STOCK_UNAVAILABLE notifications for one order)
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowReservationExpiredIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var inventoryService: InventoryService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var paymentProcessor: FakePaymentProcessor
    @Autowired lateinit var clock: MutableClock
    @Autowired lateinit var jdbcTemplate: JdbcTemplate
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    private val timeout: Duration = Duration.ofSeconds(30)

    /** A-04: reservation window of 10 minutes; the timer runs once the Clock is past it. */
    private val pastReservationWindow: Duration = Duration.ofMinutes(10).plusSeconds(1)

    @BeforeEach
    fun resetClock() {
        clock.reset()
    }

    @AfterEach
    fun releaseStalledAuthorizations() {
        // the stalled authorization throws and rolls back: payment still never resolves
        paymentProcessor.releaseStalledAuthorizations()
        // T-43: wait until the stalled call has left the processor, so it ends in this class's context, never the next
        await().atMost(timeout).until { paymentProcessor.stalledOrderIds.isEmpty() }
        // T-43: its OrderCreated publication to payments stays incomplete for good (the listener threw); drop it, and any
        // left by earlier runs on the shared test database, so no later context resubmits it and stalls again
        transactionTemplate.executeWithoutResult {
            jdbcTemplate.update(
                "DELETE FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE ?",
                "%\"${FakePaymentProcessor.NO_ANSWER_PAYMENT_METHOD}\"%",
            )
        }
        clock.reset()
    }

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

    @Test
    fun stalledPaymentEndsInOrderCancelledNotificationSentWithReservationExpired(scenario: Scenario) {
        // StartOrderCheckout commits on its own: OrderCreated reaches reserveStock and authorizePayment
        val orderId = ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()).orderId!!
        await().atMost(timeout).untilAsserted {
            assertThat(stockReservationRepository.findByOrderId(orderId).map { it.status })
                .containsExactly(ReservationStatus.RESERVED)
            assertThat(paymentProcessor.stalledOrderIds).contains(orderId)
        }
        val reservedAt = TestClock.NOW
        assertThat(stockReservationRepository.findByOrderId(orderId).single().expiresAt)
            .isEqualTo(reservedAt.plus(Duration.ofMinutes(10)))

        // payment never resolves; the Clock passes 10 minutes after OrderCreated
        clock.advance(pastReservationWindow)
        val expiredAt = clock.instant()
        val due = stockReservationRepository.findByStatusAndExpiresAtLessThanEqual(ReservationStatus.RESERVED, expiredAt)
        assertThat(due.map { it.orderId }).contains(orderId, "ORD-014")
        val releasedSku001 = due.filter { it.sku == "SKU-001" }.sumOf { it.quantity!!.toInt() }
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val available = before.availableQuantity!!.toInt()
        val reserved = before.reservedQuantity!!.toInt()
        val sold = before.soldQuantity!!.toInt()

        val job = ReservationExpiredJob(stockReservationRepository, inventoryService, clock)
        scenario
            .stimulate(Supplier { job.releaseExpiredReservations() })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderCancelledNotificationSent::class.java)
            .matching { it.orderId == orderId }
            .toArriveAndVerify(
                BiConsumer { event: OrderCancelledNotificationSent, _: Unit ->
                    assertThat(event.customerId).isEqualTo("CUST-011")
                    assertThat(event.reason).isEqualTo("RESERVATION_EXPIRED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer11@example.com")
                    assertThat(event.sentAt).isEqualTo(expiredAt)
                    assertEndState(orderId, reservedAt, expiredAt)
                    // A-02: every released SKU-001 reservation of this timer run goes back to available
                    val position = inventoryPositionRepository.findBySku("SKU-001")!!
                    assertThat(
                            listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity)
                                .map { it?.toInt() }
                        )
                        .containsExactly(available + releasedSku001, reserved - releasedSku001, sold)
                    assertC14Released(expiredAt)
                }
            )
    }

    private fun assertEndState(orderId: String, reservedAt: Instant, expiredAt: Instant) {
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED))
                .isNotNull()
        }

        // orders.checkout: startOrderCheckout -> CREATED, cancelOrder -> CANCELLED (A-07)
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.customerId).isEqualTo("CUST-011")
        assertThat(order.createdAt).isEqualTo(reservedAt)
        assertThat(order.cancelledAt).isEqualTo(expiredAt)
        assertThat(order.cancellationReason).isEqualTo("RESERVATION_EXPIRED")
        assertThat(order.confirmedAt).isNull()

        // catalog.inventory: reserveStock -> RESERVED, ReservationExpired releaseStock -> RELEASED (A-02, A-04)
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.sku).isEqualTo("SKU-001")
        assertThat(reservation.quantity).isEqualTo(1)
        assertThat(reservation.reservedAt).isEqualTo(reservedAt)
        assertThat(reservation.expiresAt).isEqualTo(reservedAt.plus(Duration.ofMinutes(10)))
        assertThat(reservation.releasedAt).isEqualTo(expiredAt)
        assertThat(reservation.releaseReason.toString()).isEqualTo("RESERVATION_EXPIRED")
        assertThat(reservation.confirmedAt).isNull()

        // payments.processing: the authorization is still waiting for the processor, nothing resolved
        assertThat(paymentProcessor.stalledOrderIds).contains(orderId)
        assertThat(paymentRepository.findByOrderId(orderId)).isNull()

        // fulfillment.shipping: nothing scheduled
        assertThat(fulfillmentRepository.findByOrderId(orderId)).isNull()

        // notifications.consumer: sendOrderCancelledNotification -> SENT, one e-mail, none for confirmation
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer11@example.com")
        assertThat(notification.createdAt).isEqualTo(expiredAt)
        assertThat(notification.sentAt).isEqualTo(expiredAt)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }

    /** C-14: RES-014 was past its deadline (11:55) at the test "now"; the same timer run releases it. */
    private fun assertC14Released(expiredAt: Instant) {
        await().atMost(timeout).untilAsserted {
            assertThat(orderRepository.findByOrderId("ORD-014")!!.status).isEqualTo(OrderStatus.CANCELLED)
        }
        val reservation = stockReservationRepository.findByOrderId("ORD-014").single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.releasedAt).isEqualTo(expiredAt)
        assertThat(reservation.releaseReason.toString()).isEqualTo("RESERVATION_EXPIRED")
        assertThat(orderRepository.findByOrderId("ORD-014")!!.cancellationReason).isEqualTo("RESERVATION_EXPIRED")
    }
}
