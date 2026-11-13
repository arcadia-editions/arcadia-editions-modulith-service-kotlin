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
 * place-order-flow, path `orderCancelled` by retries exhausted (place-order-flow.zfl:124-130, 223-260, end 262-266):
 * StartOrderCheckout with pm_card_processorError (every attempt fails, 3 attempts, A-03) ends in OrderCancelledNotificationSent, stock back to available.
 * End state is the shape of C-09 (CUST-009, SKU-001 x1). Not @Transactional: the module listeners run after commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
@EnableScenarios
@Import(TestClockConfiguration::class)
class PlaceOrderFlowRetryExhaustedIntegrationTest {

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

        scenario
            .stimulate(Supplier { ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()) })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderCancelledNotificationSent::class.java)
            .toArriveAndVerify(
                BiConsumer { event: OrderCancelledNotificationSent, order: Order ->
                    assertThat(event.orderId).isEqualTo(order.orderId)
                    assertThat(event.customerId).isEqualTo("CUST-009")
                    assertThat(event.reason).isEqualTo("PAYMENT_RETRY_EXHAUSTED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer09@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                    assertEndState(order.orderId!!, available, reserved, sold)
                }
            )
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

        // payments.processing: authorizePayment fails, retryPayment x2, 3rd failure then retryPayment -> RETRY_EXHAUSTED (A-01, A-03)
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
