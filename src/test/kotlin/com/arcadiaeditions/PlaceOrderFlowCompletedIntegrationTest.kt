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
import com.arcadiaeditions.fulfillment.shipping.FulfillmentShippingService
import com.arcadiaeditions.fulfillment.shipping.domain.FulfillmentTransitions
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
import com.arcadiaeditions.payments.processing.adapter.driven.processor.FakePaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.application.port.driven.processor.AuthorizationRequest
import com.arcadiaeditions.payments.processing.application.port.driven.processor.CaptureRequest
import com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessorResult
import com.arcadiaeditions.payments.processing.application.port.driven.processor.VoidRequest
import com.arcadiaeditions.payments.processing.domain.PaymentStatus
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Supplier
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.modulith.test.EnableScenarios
import org.springframework.modulith.test.Scenario
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * place-order-flow, path `completed` (place-order-flow.zfl:73-192, end 262-266): from StartOrderCheckout across
 * orders.checkout, catalog.inventory, payments.processing, fulfillment.shipping and notifications.consumer to
 * OrderConfirmationSent. Input is the C-06 order (CUST-006, SKU-001 x1, pm_card_visa); the end state is the shape of
 * C-06 (fulfillment SCHEDULED, paymentCapturedAt set by recordPaymentCaptured, place-order-flow.zfl:215-221).
 * T-55: [ObservingPaymentProcessor] wraps FakePaymentProcessor to look at the committed state while a processor call is
 * in flight: during authorize the Payment is AUTHORIZATION_REQUESTED (T-52); during capture the Fulfillment is SCHEDULED
 * without paymentCapturedAt and markReadyToShip is refused (T-54). After the flow end, markReadyToShip succeeds (the
 * warehouse action, outside the flow). Not @Transactional: the module listeners run after commit.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // T-43: no replay, at startup, of incomplete publications another context or run left in event_publication
    properties = ["spring.modulith.events.republish-outstanding-events-on-restart=false"],
)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
@EnableScenarios
@Import(TestClockConfiguration::class, PlaceOrderFlowCompletedIntegrationTest.ObservingProcessorConfiguration::class)
// T-43: a fresh context per flow class (schema + data.sql reloaded, new async threads). Scenario captures events through
// an InheritableThreadLocal, which async listener threads already created in a reused context never see.
@DirtiesContext
// T-43: no resubmitIncompletePublications tick: its first run, right after startup, resubmits this test's in-flight
// publications and the listener runs twice (seen: two STOCK_UNAVAILABLE notifications for one order)
@MockitoBean(types = [ExternalizationConfiguration::class])
class PlaceOrderFlowCompletedIntegrationTest {

    @Autowired lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var stockReservationRepository: StockReservationRepository
    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var emailSender: FakeEmailSender
    @Autowired lateinit var fulfillmentShippingService: FulfillmentShippingService
    @Autowired lateinit var observingPaymentProcessor: ObservingPaymentProcessor
    @Autowired lateinit var clock: MutableClock

    private val timeout: Duration = Duration.ofSeconds(30)

    @BeforeEach
    fun resetClock() {
        clock.reset()
        observingPaymentProcessor.duringAuthorize.clear()
        observingPaymentProcessor.duringCapture.clear()
    }

    /** C-06 input: the checkout of CUST-006. */
    private fun startOrderCheckoutInput() =
        StartOrderCheckoutInput(
            customerId = "CUST-006",
            customerEmail = "customer06@example.com",
            paymentMethodId = "pm_card_visa",
            currency = "EUR",
            items = mutableListOf(OrderItemInput("SKU-001", 1 as Integer, BigDecimal("49.99"))),
            shippingAddress =
                ShippingAddressInput("Alex Example", "Calle Ficticia 1", "2B", "Madrid", "Madrid", "28001", "ES"),
        )

    @Test
    fun startOrderCheckoutEndsInOrderConfirmationSent(scenario: Scenario) {
        // data/jpa/InventoryPosition/1.json: SKU-001 available 89, reserved 6, sold 5 in a fresh context; read, not
        // asserted: counters are checked relative to these (T-43)
        val before = inventoryPositionRepository.findBySku("SKU-001")!!
        val (available, reserved, sold) =
            listOf(before.availableQuantity, before.reservedQuantity, before.soldQuantity).map { it!!.toInt() }

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
                    assertEndState(order.orderId!!, available, reserved, sold)
                    assertInFlightStates(order.orderId!!)
                    assertMarkReadyToShipAfterCapture(order.orderId!!)
                }
            )
    }

    private fun assertEndState(orderId: String, available: Int, reserved: Int, sold: Int) {
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
            .containsExactly(available - 1, reserved, sold + 1)

        // payments.processing: authorizePayment -> AUTHORIZED (pm_card_visa), capturePayment -> CAPTURED (A-01)
        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.status).isEqualTo(PaymentStatus.CAPTURED)
        assertThat(payment.attemptCount).isEqualTo(1)
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
        // recordPaymentCaptured (zfl:215-221) runs apart from sendOrderConfirmation: wait for its commit
        await().atMost(timeout).untilAsserted {
            assertThat(fulfillmentRepository.findByOrderId(orderId)!!.paymentCapturedAt).isEqualTo(now)
        }
        assertThat(fulfillmentRepository.findByOrderId(orderId)!!.status).isEqualTo(FulfillmentStatus.SCHEDULED)

        // notifications.consumer: sendOrderConfirmation -> SENT (A-01), one e-mail to the customer
        val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)!!
        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(notification.recipient).isEqualTo("customer06@example.com")
        assertThat(notification.createdAt).isEqualTo(now)
        assertThat(notification.sentAt).isEqualTo(now)
        assertThat(emailSender.sent.filter { it.body.contains(orderId) }).hasSize(1)
    }

    /** What the committed state looked like while the processor calls of this order were in flight. */
    private fun assertInFlightStates(orderId: String) {
        // T-52: AUTHORIZATION_REQUESTED (attempt 1) is committed before the processor is called, then AUTHORIZED
        val authorizing = observingPaymentProcessor.duringAuthorize[orderId]
        assertThat(authorizing).isNotNull()
        assertThat(authorizing!!.status).isEqualTo(PaymentStatus.AUTHORIZATION_REQUESTED)
        assertThat(authorizing.attemptCount).isEqualTo(1)
        assertThat(authorizing.authorizedAt).isNull()

        // T-54: while capture is in flight the Fulfillment is SCHEDULED, not yet captured, and markReadyToShip refused
        val capturing = observingPaymentProcessor.duringCapture[orderId]
        assertThat(capturing).isNotNull()
        assertThat(capturing!!.paymentStatus).isEqualTo(PaymentStatus.AUTHORIZED)
        assertThat(capturing.fulfillmentStatus).isEqualTo(FulfillmentStatus.SCHEDULED)
        assertThat(capturing.paymentCapturedAt).isNull()
        assertThat(capturing.markReadyToShipError)
            .isInstanceOf(FulfillmentTransitions.InvalidStateTransitionException::class.java)
        assertThat(capturing.fulfillmentStatusAfterRefusal).isEqualTo(FulfillmentStatus.SCHEDULED)
    }

    /** T-54: once paymentCapturedAt is recorded, the warehouse action markReadyToShip succeeds. */
    private fun assertMarkReadyToShipAfterCapture(orderId: String) {
        val fulfillment = fulfillmentRepository.findByOrderId(orderId)!!
        val ready = fulfillmentShippingService.markReadyToShip(fulfillment.id!!)
        assertThat(ready.status).isEqualTo(FulfillmentStatus.READY_TO_SHIP)
        val stored = fulfillmentRepository.findByOrderId(orderId)!!
        assertThat(stored.status).isEqualTo(FulfillmentStatus.READY_TO_SHIP)
        assertThat(stored.paymentCapturedAt).isEqualTo(TestClock.NOW)
    }

    data class AuthorizeSnapshot(val status: PaymentStatus?, val attemptCount: Int?, val authorizedAt: Instant?)

    data class CaptureSnapshot(
        val paymentStatus: PaymentStatus?,
        val fulfillmentStatus: FulfillmentStatus?,
        val paymentCapturedAt: Instant?,
        val markReadyToShipError: Throwable?,
        val fulfillmentStatusAfterRefusal: FulfillmentStatus?,
    )

    /**
     * Delegates every call to FakePaymentProcessor. Before delegating, reads the committed state of the order on another
     * thread (so no transaction of the caller is joined) and, during capture, tries markReadyToShip there.
     */
    class ObservingPaymentProcessor(
        private val delegate: FakePaymentProcessor,
        private val paymentRepository: ObjectProvider<PaymentRepository>,
        private val fulfillmentRepository: ObjectProvider<FulfillmentRepository>,
        private val fulfillmentShippingService: ObjectProvider<FulfillmentShippingService>,
    ) : PaymentProcessor {

        val duringAuthorize: MutableMap<String, AuthorizeSnapshot> = ConcurrentHashMap()
        val duringCapture: MutableMap<String, CaptureSnapshot> = ConcurrentHashMap()

        override fun authorize(request: AuthorizationRequest): PaymentProcessorResult {
            val snapshot = apart {
                val payment = paymentRepository.getObject().findByOrderId(request.orderId)
                AuthorizeSnapshot(payment?.status, payment?.attemptCount?.toInt(), payment?.authorizedAt)
            }
            duringAuthorize.putIfAbsent(request.orderId, snapshot)
            return delegate.authorize(request)
        }

        override fun capture(request: CaptureRequest): PaymentProcessorResult {
            val snapshot = apart {
                val payment = paymentRepository.getObject().findByOrderId(request.orderId)
                val fulfillment = fulfillmentRepository.getObject().findByOrderId(request.orderId)
                val error =
                    fulfillment?.id?.let { id ->
                        runCatching { fulfillmentShippingService.getObject().markReadyToShip(id) }.exceptionOrNull()
                    }
                CaptureSnapshot(
                    paymentStatus = payment?.status,
                    fulfillmentStatus = fulfillment?.status,
                    paymentCapturedAt = fulfillment?.paymentCapturedAt,
                    markReadyToShipError = error,
                    fulfillmentStatusAfterRefusal =
                        fulfillmentRepository.getObject().findByOrderId(request.orderId)?.status,
                )
            }
            duringCapture.putIfAbsent(request.orderId, snapshot)
            return delegate.capture(request)
        }

        override fun void(request: VoidRequest): PaymentProcessorResult = delegate.void(request)

        private fun <T> apart(block: () -> T): T = CompletableFuture.supplyAsync { block() }.get()
    }

    /** The observer replaces FakePaymentProcessor as the PaymentProcessor the payments service is given. */
    @TestConfiguration(proxyBeanMethods = false)
    class ObservingProcessorConfiguration {
        @Bean
        @Primary
        fun observingPaymentProcessor(
            delegate: FakePaymentProcessor,
            paymentRepository: ObjectProvider<PaymentRepository>,
            fulfillmentRepository: ObjectProvider<FulfillmentRepository>,
            fulfillmentShippingService: ObjectProvider<FulfillmentShippingService>,
        ) = ObservingPaymentProcessor(delegate, paymentRepository, fulfillmentRepository, fulfillmentShippingService)
    }
}
