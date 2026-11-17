package com.arcadiaeditions

import com.arcadiaeditions.catalog.inventory.repository.jpa.InventoryPositionRepository
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.config.DockerComposeInitializer
import com.arcadiaeditions.config.ExternalizationConfiguration
import com.arcadiaeditions.config.MutableClock
import com.arcadiaeditions.config.TestClock
import com.arcadiaeditions.config.TestClockConfiguration
import com.arcadiaeditions.notifications.consumer.FakeEmailSender
import com.arcadiaeditions.notifications.consumer.domain.NotificationChannel
import com.arcadiaeditions.notifications.consumer.domain.NotificationStatus
import com.arcadiaeditions.notifications.consumer.domain.NotificationType
import com.arcadiaeditions.notifications.consumer.domain.events.StockUnavailableNotificationSent
import com.arcadiaeditions.notifications.consumer.repository.jpa.NotificationRepository
import com.arcadiaeditions.orders.checkout.core.inbound.OrdersCheckoutService
import com.arcadiaeditions.orders.checkout.core.inbound.StockUnavailableException
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.OrderItemInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.ShippingAddressInput
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.StartOrderCheckoutInput
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.OrderRepository
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
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
 * place-order-flow, path `stockGone` (place-order-flow.zfl:82-99, end 262-266): StartOrderCheckout for SKU-003
 * (available 0) emits StockUnavailable and ends in StockUnavailableNotificationSent; no order, reservation or payment
 * (C-07 shape, A-05). Not @Transactional: the module listeners run after commit.
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
class PlaceOrderFlowStockGoneIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    @BeforeEach
    fun resetClock() {
        clock.reset()
    }

    /** C-07 input: the checkout of CUST-007 for the sold-out SKU-003. */
    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-007",
            customerEmail = "customer07@example.com",
            paymentMethodId = "pm_card_visa",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-003", 1 as Integer, BigDecimal("75.00"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun startOrderCheckoutForSoldOutSkuEndsInStockUnavailableNotificationSent(scenario: Scenario) {
        // data/jpa/InventoryPosition/3.json: SKU-003 available 0, reserved 0, sold 10
        assertCounters()

        scenario
            .stimulate(
                Supplier {
                    try {
                        ordersCheckoutService.startOrderCheckout(startOrderCheckoutInput())
                        throw AssertionError("startOrderCheckout of a sold-out SKU must throw StockUnavailableException")
                    } catch (e: StockUnavailableException) {
                        e
                    }
                }
            )
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(StockUnavailableNotificationSent::class.java)
            .toArriveAndVerify(
                BiConsumer { event: StockUnavailableNotificationSent, thrown: StockUnavailableException ->
                    // startOrderCheckout reports the sold-out stock to its caller (A-05)
                    val orderId = thrown.event.orderId!!
                    assertThat(thrown.event.customerId).isEqualTo("CUST-007")
                    assertThat(thrown.event.unavailableItems!!.map { it.sku }).containsExactly("SKU-003")

                    assertThat(event.orderId).isEqualTo(orderId)
                    assertThat(event.customerId).isEqualTo("CUST-007")
                    assertThat(event.skus).containsExactly("SKU-003")
                    assertThat(event.channel).isEqualTo(NotificationChannel.EMAIL)
                    assertThat(event.recipient).isEqualTo("customer07@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                    assertEndState(orderId)
                }
            )
    }

    private fun assertCounters() {
        val position = inventoryPositionRepository.findBySku("SKU-003")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(0, 0, 10)
    }

    private fun assertEndState(orderId: String) {
        val now = TestClock.NOW
        // the event is published inside the sendStockUnavailableNotification transaction: wait for its commit
        await().atMost(timeout).untilAsserted {
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.STOCK_UNAVAILABLE))
                .isNotNull()
        }

        // notifications.consumer: sendStockUnavailableNotification -> SENT (A-01), one e-mail to the customer
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.STOCK_UNAVAILABLE)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.customerId).isEqualTo("CUST-007")
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer07@example.com")
        assertThat(notification.skus).containsExactly("SKU-003")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)

        // no order, reservation or payment (A-05); stock counters unchanged
        assertThat(orderRepository.findByOrderId(orderId)).isNull()
        assertThat(stockReservationRepository.findByOrderId(orderId)).isEmpty()
        assertThat(paymentRepository.findByOrderId(orderId)).isNull()
        assertCounters()
    }
}
