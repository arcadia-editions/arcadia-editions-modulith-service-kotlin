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
import com.arcadiaeditions.payments.processing.domain.event.PaymentAuthorized
import com.arcadiaeditions.payments.processing.domain.event.PaymentFailed
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetried
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
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
 * place-order-flow, path `retry` (place-order-flow.zfl:114-146), C-12 fixture (ORD-012, Payment FAILED attempt 1) resumes
 * from its pending PaymentFailed. Its own class: Spring Modulith 2.0 binds the Scenario listener to the first context of a
 * class only (T-82). Not @Transactional: the module listeners run after commit.
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
class PlaceOrderFlowRetryPendingFailedIntegrationTest {

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

    /**
     * C-12: ORD-012 is CREATED, RES-012 RESERVED, Payment FAILED at attempt 1 with PaymentFailed pending. A PaymentRetried
     * whose attemptCount is not the Payment's (2, out of order) is a no-op; the pending PaymentFailed (attempt 1) runs the
     * loop: retryPayment retried, the 5 s backoff, reauthorizePayment attempt 2 (pm_card_visa approves), and the flow ends
     * in OrderConfirmationSent for ORD-012.
     */
    @Test
    fun c12PendingPaymentFailedIsRetriedAndEndsInOrderConfirmationSent(scenario: Scenario) {
        val orderId = "ORD-012"
        recorder.orderId = orderId
        val fixture = paymentRepository.findByOrderId(orderId)!!
        assertThat(fixture.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(fixture.attemptCount!!.toInt()).isEqualTo(1)
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }
        val mark = events.mark()

        // out of order: attemptCount 2 is not the attempt that failed
        events.redeliver(
            PaymentRetried(orderId, "CUST-012", 2 as Integer, Instant.parse("2026-10-01T11:59:30Z")),
            timeout,
        )
        assertThat(paymentRepository.findByOrderId(orderId)!!.snapshot()).isEqualTo(fixture.snapshot())
        assertThat(recorder.steps).isEmpty()

        scenario
            .publish(PaymentFailed(orderId, "CUST-012", 1 as Integer, "processor_timeout", Instant.parse("2026-10-01T11:59:30Z")))
            .andWaitAtMost(timeout)
            .andWaitForEventOfType(OrderConfirmationSent::class.java)
            .matching(Predicate { it.orderId == orderId })
            .toArriveAndVerify(
                Consumer { event: OrderConfirmationSent ->
                    assertThat(event.customerId).isEqualTo("CUST-012")
                    assertThat(event.recipient).isEqualTo("customer12@example.com")
                    assertThat(event.sentAt).isEqualTo(TestClock.NOW)
                }
            )

        assertThat(recorder.steps)
            .containsExactly(
                Backoff(Duration.ofSeconds(5), PaymentStatus.FAILED, 1),
                Authorize(2, PaymentStatus.AUTHORIZATION_REQUESTED, 2),
            )
        assertThat(
                events.since(mark, PaymentRetried::class.java, RetryLoopTargets.REAUTHORIZE_PAYMENT) { it.orderId == orderId }
            )
            .extracting<Int> { it.attemptCount!!.toInt() }
            .containsExactlyInAnyOrder(2, 1)
        await().atMost(timeout).untilAsserted {
            assertThat(paymentRepository.findByOrderId(orderId)!!.status).isEqualTo(PaymentStatus.CAPTURED)
            assertThat(notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)).isNotNull()
        }
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.attemptCount!!.toInt()).isEqualTo(2)
        assertThat(payment.authorizedAt).isEqualTo(TestClock.NOW)
        assertThat(payment.failedAt).isNull()
        assertThat(payment.failureReason).isNull()
        assertThat(orderRepository.findByOrderId(orderId)!!.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(stockReservationRepository.findByOrderId(orderId).single().status).isEqualTo(ReservationStatus.CONFIRMED)
        val position = inventoryPositionRepository.findBySku("SKU-001")!!
        assertThat(listOf(position.availableQuantity, position.reservedQuantity, position.soldQuantity).map { it?.toInt() })
            .containsExactly(available, reserved - 1, sold + 1)
    }
}
