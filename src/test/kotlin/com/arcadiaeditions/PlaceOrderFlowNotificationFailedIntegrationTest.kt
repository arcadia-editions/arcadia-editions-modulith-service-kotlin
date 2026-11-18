package com.arcadiaeditions

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
import com.arcadiaeditions.notifications.consumer.domain.events.NotificationDeliveryFailed
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
 * place-order-flow, path `notificationFailed` (place-order-flow.zfl:202-213, end 336-345): the checkout runs as in the
 * completed path, the e-mail provider refuses the order confirmation (FakeEmailSender.refusalReason, A-10), the
 * Notification is FAILED and the flow ends at NotificationDeliveryFailed with type ORDER_CONFIRMATION. Shape of C-29
 * (CUST-029). Not @Transactional: the module listeners run after commit.
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
// T-43: a fresh context per flow class (see PlaceOrderFlowCompletedIntegrationTest)
@DirtiesContext
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowNotificationFailedIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    @BeforeEach
    fun prepare() {
        clock.reset()
        emailSender.refusalReason = "smtp_rejected"
    }

    @AfterEach
    fun restoreFakes() {
        emailSender.refusalReason = null
    }

    /** C-29 input: the checkout of CUST-029. */
    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-029",
            customerEmail = "customer29@example.com",
            paymentMethodId = "pm_card_visa",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-006", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun refusedConfirmationEndsInNotificationDeliveryFailed(scenario: Scenario) {
        // SKU-006 counters are checked relative to the position read before the checkout (T-43)
        val before = inventoryPositionRepository.findBySku("SKU-006")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }

        scenario
            .stimulate(Supplier { ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput()) })
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(NotificationDeliveryFailed::class.java)
            .toArriveAndVerify(
                BiConsumer { event: NotificationDeliveryFailed, order: Order ->
                    assertThat(event.orderId).isEqualTo(order.orderId)
                    assertThat(event.customerId).isEqualTo("CUST-029")
                    assertThat(event.type).isEqualTo(NotificationType.ORDER_CONFIRMATION)
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer29@example.com")
                    assertThat(event.reason).isEqualTo("smtp_rejected")
                    assertThat(event.failedAt).isEqualTo(TestClock.NOW)
                    assertEndState(order.orderId!!, event.notificationId, available, reserved, sold)
                }
            )
    }

    private fun assertEndState(orderId: String, eventNotificationId: String?, available: Int, reserved: Int, sold: Int) {
        val now = TestClock.NOW
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION))
                .isNotNull()
        }

        // the business outcome is untouched: order CONFIRMED, stock CONFIRMED, payment CAPTURED, fulfillment SCHEDULED
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(order.cancelledAt).isNull()
        assertThat(stockReservationRepository.findByOrderId(orderId).single().status)
            .isEqualTo(ReservationStatus.CONFIRMED)
        assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(fulfillmentRepository.findByOrderId(orderId)!!.status).isEqualTo(FulfillmentStatus.SCHEDULED)
        val position = inventoryPositionRepository.findBySku("SKU-006")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available - 1, reserved, sold + 1)
        // recordPaymentCaptured (zfl:215-221) runs apart from sendOrderConfirmation: wait for its commit
        await().atMost(timeout).untilAsserted {
            assertThat(fulfillmentRepository.findByOrderId(orderId)!!.paymentCapturedAt).isEqualTo(now)
        }

        // notifications.consumer: sendOrderConfirmation refused -> FAILED, nothing sent
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer29@example.com")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isNull()
        assertThat(notification.failedAt).isEqualTo(now)
        assertThat(notification.failureReason).isEqualTo("smtp_rejected")
        assertThat(eventNotificationId).isEqualTo(notification.notificationId)
    }
}
