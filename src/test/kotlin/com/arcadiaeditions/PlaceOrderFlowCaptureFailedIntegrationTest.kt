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
import com.arcadiaeditions.notifications.consumer.domain.events.OrderCancelledNotificationSent
import com.arcadiaeditions.notifications.consumer.repository.jpa.NotificationRepository
import com.arcadiaeditions.orders.checkout.core.domain.Order
import com.arcadiaeditions.orders.checkout.core.domain.OrderStatus
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.OrderItemInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.ShippingAddressInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.StartOrderCheckoutInput
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository
import com.arcadiaeditions.payments.processing.adapter.driven.processor.FakePaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessorResult
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
 * place-order-flow, path `orderCancelled` by capture failed (place-order-flow.zfl:172-182, 204-260, end 262-266):
 * FakePaymentProcessor refuses the capture (capture_window_closed), PaymentCaptureFailed voids the authorization, PaymentVoided releases the
 * stock, OrderCancelled ends in OrderCancelledNotificationSent with PAYMENT_VOIDED. Input is the C-10 customer
 * (CUST-010, SKU-001 x1); the capture failure itself is the shape of C-17. FakePaymentProcessor is a singleton bean: reset after the test. Not @Transactional: the module
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
@Import(TestClockConfiguration::class)
// T-43: a fresh context per flow class (schema + data.sql reloaded, new async threads). Scenario captures events through
// an InheritableThreadLocal, which async listener threads already created in a reused context never see.
@DirtiesContext
// T-43: no resubmitIncompletePublications tick: its first run, right after startup, resubmits this test's in-flight
// publications and the listener runs twice (seen: two STOCK_UNAVAILABLE notifications for one order)
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowCaptureFailedIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var paymentProcessor: FakePaymentProcessor
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    @BeforeEach
    fun prepare() {
        clock.reset()
        paymentProcessor.captureRefusal = PaymentProcessorResult.failed("capture_window_closed")
    }

    @AfterEach
    fun restoreProcessor() {
        paymentProcessor.captureRefusal = null
    }

    /** C-17 input: the checkout of CUST-010. */
    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-010",
            customerEmail = "customer10@example.com",
            paymentMethodId = "pm_card_visa",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-001", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun captureFailedEndsInOrderCancelledNotificationSentWithPaymentVoided(scenario: Scenario) {
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
                    assertThat(event.customerId).isEqualTo("CUST-010")
                    assertThat(event.reason).isEqualTo("PAYMENT_VOIDED")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer10@example.com")
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

        // orders.checkout: startOrderCheckout -> CREATED, confirmOrder -> CONFIRMED, cancelOrder -> CANCELLED
        val order = orderRepository.findByOrderId(orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        assertThat(order.customerId).isEqualTo("CUST-010")
        assertThat(order.createdAt).isEqualTo(now)
        assertThat(order.confirmedAt).isEqualTo(now)
        assertThat(order.cancelledAt).isEqualTo(now)
        assertThat(order.cancellationReason).isEqualTo("PAYMENT_VOIDED")

        // catalog.inventory: reserveStock -> RESERVED, confirmStockReservation -> CONFIRMED, releaseStock -> RELEASED
        val reservation = stockReservationRepository.findByOrderId(orderId).single()
        assertThat(reservation.status).isEqualTo(ReservationStatus.RELEASED)
        assertThat(reservation.sku).isEqualTo("SKU-001")
        assertThat(reservation.quantity).isEqualTo(1)
        assertThat(reservation.reservedAt).isEqualTo(now)
        assertThat(reservation.confirmedAt).isEqualTo(now)
        assertThat(reservation.releasedAt).isEqualTo(now)
        assertThat(reservation.releaseReason.toString()).isEqualTo("PAYMENT_VOIDED")
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved, sold)

        // payments.processing: authorizePayment -> AUTHORIZED, capturePayment -> CAPTURE_FAILED, voidPayment -> VOIDED, never captured
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.VOIDED)
        assertThat(payment.attemptCount).isEqualTo(1)
        assertThat(payment.authorizedAt).isEqualTo(now)
        assertThat(payment.voidedAt).isEqualTo(now)
        assertThat(payment.capturedAt).isNull()
        assertThat(payment.declinedAt).isNull()
        assertThat(payment.failedAt).isEqualTo(now)
        assertThat(payment.failureReason).isEqualTo("capture_window_closed")

        // fulfillment.shipping: scheduleFulfillment -> SCHEDULED (not undone by the capture failure)
        val fulfillment = fulfillmentRepository.findByOrderId(orderId)!!
        assertThat(fulfillment.status).isEqualTo(FulfillmentStatus.SCHEDULED)
        assertThat(fulfillment.customerId).isEqualTo("CUST-010")
        assertThat(fulfillment.scheduledAt).isEqualTo(now)
        assertThat(fulfillment.failedAt).isNull()

        // notifications.consumer: sendOrderCancelledNotification -> SENT, one e-mail, none for confirmation
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer10@example.com")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNull()
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }
}
