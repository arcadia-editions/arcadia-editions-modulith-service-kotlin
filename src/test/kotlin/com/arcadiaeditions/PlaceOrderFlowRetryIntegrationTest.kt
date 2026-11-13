package com.arcadiaeditions

import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.repository.jpa.InventoryPositionRepository
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.config.DockerComposeInitializer
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
import org.springframework.test.context.ActiveProfiles

/**
 * place-order-flow, path `retry` (place-order-flow.zfl:108-140, end 262-266): the first authorization fails
 * (pm_card_processorErrorOnce, shape of C-12), PaymentFailed -> retryPayment -> PaymentRetried -> authorizePayment
 * approves attempt 2, and the flow ends in OrderConfirmationSent. From StartOrderCheckout across
 * orders.checkout, catalog.inventory, payments.processing, fulfillment.shipping and notifications.consumer to
 * OrderConfirmationSent. Input is the C-06 order (CUST-006, SKU-001 x1) with the retry payment method; the end state is the shape of
 * C-06 with payment attemptCount 2 (fulfillment SCHEDULED: READY_TO_SHIP comes later, outside the flow). Not @Transactional: the module listeners
 * run after commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
@EnableScenarios
@Import(TestClockConfiguration::class)
class PlaceOrderFlowRetryIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    @BeforeEach
    fun resetClock() {
        clock.reset()
    }

    /** C-06 input: the checkout of CUST-006. */
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
        // data/jpa/InventoryPosition/1.json: SKU-001 available 89, reserved 6, sold 5
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it?.toInt() })
            .containsExactly(89, 6, 5)

        scenario
            .stimulate(Supplier { ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()) })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderConfirmationSent::class.java)
            .toArriveAndVerify(
                BiConsumer { event: OrderConfirmationSent, order: Order ->
                    assertThat(event.orderId).isEqualTo(order.orderId)
                    assertThat(event.customerId).isEqualTo("CUST-006")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer06@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                    assertEndState(order.orderId!!)
                }
            )
    }

    private fun assertEndState(orderId: String) {
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
            .containsExactly(88, 6, 6)

        // payments.processing: authorizePayment fails (attempt 1), retryPayment, authorizePayment -> AUTHORIZED (attempt 2), capturePayment -> CAPTURED (A-01)
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
