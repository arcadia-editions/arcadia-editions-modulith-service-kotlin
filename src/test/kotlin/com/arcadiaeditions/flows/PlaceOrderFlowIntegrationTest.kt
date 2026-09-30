package com.arcadiaeditions.flows

import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.catalog.inventory.service.InventoryBackofficeService
import com.arcadiaeditions.catalog.inventory.service.dtos.CreateInventoryPositionInput
import com.arcadiaeditions.config.DockerComposeInitializer
import com.arcadiaeditions.fulfillment.shipping.adapters.stub.StubFulfillmentCarrier
import com.arcadiaeditions.fulfillment.shipping.domain.FulfillmentStatus
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.FulfillmentRepository
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleOutcome
import com.arcadiaeditions.notifications.consumer.adapters.stub.StubNotificationSender
import com.arcadiaeditions.notifications.consumer.domain.NotificationStatus
import com.arcadiaeditions.notifications.consumer.domain.NotificationType
import com.arcadiaeditions.notifications.consumer.repository.jpa.NotificationRepository
import com.arcadiaeditions.orders.checkout.domain.OrderStatus
import com.arcadiaeditions.orders.checkout.repository.jpa.OrderRepository
import com.arcadiaeditions.orders.checkout.service.OrdersCheckoutService
import com.arcadiaeditions.orders.checkout.service.StockUnavailableException
import com.arcadiaeditions.orders.checkout.service.dtos.OrderItemInput
import com.arcadiaeditions.orders.checkout.service.dtos.ShippingAddressInput
import com.arcadiaeditions.orders.checkout.service.dtos.StartOrderCheckoutInput
import com.arcadiaeditions.payments.processing.adapters.stub.StubPaymentProvider
import com.arcadiaeditions.payments.processing.domain.PaymentStatus
import com.arcadiaeditions.payments.processing.repository.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.service.ports.AuthorizeOutcome
import com.arcadiaeditions.payments.processing.service.ports.CaptureOutcome
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * G-FT-01: end-to-end flow tests for `place-order-flow.zfl`, one per ZFL `end` outcome, driven
 * through [OrdersCheckoutService.startOrderCheckout] against the real Spring context (Postgres via
 * [DockerComposeInitializer], the real `@ApplicationModuleListener` choreography across all six
 * modules).
 *
 * Deliberately **not** `@Transactional`: `@ApplicationModuleListener` is
 * `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` (see
 * `org.springframework.modulith.events.ApplicationModuleListener`). Wrapping the test method in a
 * transaction that only ever rolls back would mean the outer `startOrderCheckout`/`confirmOrder`/...
 * transactions never really commit, so none of the downstream listeners would ever fire. Each test
 * therefore waits for the flow to settle with Awaitility, polling real DB state through the JPA
 * repositories, and uses a fresh SKU/inventory position (and a fresh customer email/order per test)
 * so the parallel choreography of one test can never interfere with another.
 */
// WebEnvironment.MOCK (not NONE): OrdersCheckoutApiController autowires a request-scoped
// NativeWebRequest, which only exists once a (mock) web application context is present.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@DockerComposeInitializer.EnableDockerCompose
class PlaceOrderFlowIntegrationTest {

    @Autowired private lateinit var ordersCheckoutService: OrdersCheckoutService
    @Autowired private lateinit var inventoryBackofficeService: InventoryBackofficeService

    @Autowired private lateinit var orderRepository: OrderRepository
    @Autowired private lateinit var stockReservationRepository: StockReservationRepository
    @Autowired private lateinit var paymentRepository: PaymentRepository
    @Autowired private lateinit var fulfillmentRepository: FulfillmentRepository
    @Autowired private lateinit var notificationRepository: NotificationRepository

    @Autowired private lateinit var stubPaymentProvider: StubPaymentProvider
    @Autowired private lateinit var stubFulfillmentCarrier: StubFulfillmentCarrier
    @Autowired private lateinit var stubNotificationSender: StubNotificationSender

    /** Reset every forced stub outcome after each test so it never leaks into the next one. */
    @AfterEach
    fun resetStubs() {
        stubPaymentProvider.forcedAuthorizeOutcome = null
        stubPaymentProvider.forcedCaptureOutcome = null
        stubPaymentProvider.forcedVoidOutcome = null
        stubFulfillmentCarrier.forcedOutcome = null
        stubNotificationSender.forcedOutcome = null
    }

    private fun awaitFlow() = await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))

    /** Creates a fresh, uncontended [com.arcadiaeditions.catalog.inventory.domain.InventoryPosition]. */
    private fun freshSku(quantity: Int = 10): String {
        val sku = "FT-${UUID.randomUUID()}"
        inventoryBackofficeService.createInventoryPosition(CreateInventoryPositionInput(sku, quantity as java.lang.Integer))
        return sku
    }

    private fun checkoutInput(
        sku: String,
        quantity: Int = 1,
        paymentMethodId: String = "pm_card_visa",
        postalCode: String = "10001",
        customerEmail: String = "flow-${UUID.randomUUID()}@example.com",
    ) =
        StartOrderCheckoutInput(
            customerId = "CUST-FLOW-${UUID.randomUUID()}",
            customerEmail = customerEmail,
            paymentMethodId = paymentMethodId,
            currency = "EUR",
            items = mutableListOf(OrderItemInput(sku, quantity as java.lang.Integer, BigDecimal("25.00"))),
            shippingAddress =
                ShippingAddressInput(
                    name = "Flow Test",
                    line1 = "1 Test St",
                    line2 = null,
                    city = "Testville",
                    region = null,
                    postalCode = postalCode,
                    country = "US",
                ),
        )

    // -----------------------------------------------------------------------------------------
    // end { completed: OrderConfirmationSent }
    // -----------------------------------------------------------------------------------------

    @Test
    fun `completed - happy path ends with order confirmed, payment captured, fulfillment scheduled, confirmation sent`() {
        val sku = freshSku()
        val input = checkoutInput(sku)

        val created = ordersCheckoutService.startOrderCheckout(input)
        val orderId = created.orderId!!
        assertThat(created.status).isEqualTo(OrderStatus.CREATED)

        // capture and the confirmation notification happen several async hops after CONFIRMED
        // (OrderConfirmed -> scheduleFulfillment -> FulfillmentScheduled -> capturePayment -> PaymentCaptured),
        // so the whole end state is awaited, not just the order status.
        awaitFlow().untilAsserted {
            val order = orderRepository.findByOrderId(orderId)
            assertThat(order).isNotNull
            assertThat(order!!.status).isEqualTo(OrderStatus.CONFIRMED)

            val reservations = stockReservationRepository.findByOrderId(orderId)
            assertThat(reservations).isNotEmpty
            assertThat(reservations).allSatisfy { assertThat(it.status).isEqualTo(ReservationStatus.CONFIRMED) }

            val payment = paymentRepository.findByOrderId(orderId)
            assertThat(payment).isNotNull
            assertThat(payment!!.status).isEqualTo(PaymentStatus.CAPTURED)

            val fulfillment = fulfillmentRepository.findByOrderId(orderId)
            assertThat(fulfillment).isNotNull
            assertThat(fulfillment!!.status).isEqualTo(FulfillmentStatus.SCHEDULED)

            val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CONFIRMATION)
            assertThat(notification).isNotNull
            assertThat(notification!!.status).isEqualTo(NotificationStatus.SENT)
        }
    }

    // -----------------------------------------------------------------------------------------
    // end { stockGone: StockUnavailableNotificationSent }
    // -----------------------------------------------------------------------------------------

    @Test
    fun `stockGone - unavailable stock ends with 409, no order persisted, stock-unavailable notification sent`() {
        // Q-14/G-CI-01: request more than the fresh position holds.
        val sku = freshSku(quantity = 1)
        val input = checkoutInput(sku, quantity = 2)

        val exception = assertThrows(StockUnavailableException::class.java) { ordersCheckoutService.startOrderCheckout(input) }

        // Q-03/Q-15: no order is persisted for the UNAVAILABLE branch; recover the generated orderId
        // from the exception message (`"Stock unavailable for order $orderId"`, see
        // OrdersCheckoutServiceImpl.startOrderCheckout) since it is otherwise unobservable - the
        // method throws instead of returning the (never-created) Order.
        val orderId =
            Regex("order (\\S+)$").find(exception.message ?: "")?.groupValues?.get(1)
                ?: error("Could not recover orderId from exception message: ${exception.message}")

        assertThat(orderRepository.findByOrderId(orderId)).isNull()

        awaitFlow().untilAsserted {
            val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.STOCK_UNAVAILABLE)
            assertThat(notification).isNotNull
            assertThat(notification!!.status).isEqualTo(NotificationStatus.SENT)
        }

        // Still no order after the notification settled.
        assertThat(orderRepository.findByOrderId(orderId)).isNull()
    }

    // -----------------------------------------------------------------------------------------
    // end { orderCancelled: OrderCancelledNotificationSent }, by cause
    // -----------------------------------------------------------------------------------------

    @Test
    fun `orderCancelled - payment declined releases stock and cancels the order`() {
        val sku = freshSku()
        // StubPaymentProvider: paymentMethodId "pm_card_declined" forces AuthorizeOutcome.DECLINED.
        val input = checkoutInput(sku, paymentMethodId = StubPaymentProvider.DECLINED_PAYMENT_METHOD_ID)

        val created = ordersCheckoutService.startOrderCheckout(input)
        val orderId = created.orderId!!

        assertCancelledEndToEnd(orderId, expectedReason = "PAYMENT_DECLINED", expectedPaymentStatus = PaymentStatus.DECLINED)
    }

    @Test
    fun `orderCancelled - retries exhausted after 3 failed attempts releases stock and cancels the order`() {
        // Forced FAILED on every authorize/reauthorize attempt: authorizePayment -> PaymentFailed
        // triggers the payments module's own onPaymentFailed listener -> retryPayment, which
        // re-authorizes synchronously and emits another PaymentFailed each time it isn't attempt 3
        // yet - cascading through 3 attempts before RETRY_EXHAUSTED (Q-05, G-PP-03).
        stubPaymentProvider.forcedAuthorizeOutcome = AuthorizeOutcome.FAILED

        val sku = freshSku()
        val input = checkoutInput(sku)

        val created = ordersCheckoutService.startOrderCheckout(input)
        val orderId = created.orderId!!

        assertCancelledEndToEnd(
            orderId,
            expectedReason = "PAYMENT_RETRY_EXHAUSTED",
            expectedPaymentStatus = PaymentStatus.RETRY_EXHAUSTED,
        )

        val payment = paymentRepository.findByOrderId(orderId)!!
        assertThat(payment.attemptCount!!.toInt()).isEqualTo(3)
    }

    @Test
    fun `orderCancelled - fulfillment failure voids the authorized payment and cancels the order`() {
        // StubFulfillmentCarrier: postalCode "FAIL00" forces ScheduleOutcome.FAILED.
        val sku = freshSku()
        val input = checkoutInput(sku, postalCode = StubFulfillmentCarrier.FAILING_POSTAL_CODE)

        val created = ordersCheckoutService.startOrderCheckout(input)
        val orderId = created.orderId!!

        assertCancelledEndToEnd(orderId, expectedReason = "PAYMENT_VOIDED", expectedPaymentStatus = PaymentStatus.VOIDED)

        val fulfillment = fulfillmentRepository.findByOrderId(orderId)!!
        assertThat(fulfillment.status).isEqualTo(FulfillmentStatus.FAILED)
    }

    @Test
    fun `orderCancelled - capture failure voids the payment and cancels the order`() {
        val sku = freshSku()
        val input = checkoutInput(sku)

        // Only force CAPTURED to fail; authorization and fulfillment scheduling must succeed first
        // so capturePayment is actually reached. Setting this before startOrderCheckout is safe -
        // capture only happens after fulfillment is scheduled, several steps later in the chain.
        stubPaymentProvider.forcedCaptureOutcome = CaptureOutcome.FAILED

        val created = ordersCheckoutService.startOrderCheckout(input)
        val orderId = created.orderId!!

        assertCancelledEndToEnd(orderId, expectedReason = "PAYMENT_VOIDED", expectedPaymentStatus = PaymentStatus.VOIDED)

        val fulfillment = fulfillmentRepository.findByOrderId(orderId)!!
        assertThat(fulfillment.status).isEqualTo(FulfillmentStatus.SCHEDULED)
    }

    /**
     * Reservation expiry is owned by the Scheduler actor (`ReservationExpired`, `@time("10 minutes
     * after OrderCreated and not ...")`). Per the gap report (Q-07/M-08) this is intentionally
     * deferred - no `@Scheduled` poller was implemented - so there is nothing to drive this outcome
     * from yet. Once Q-07 is resolved and a poller exists, this test should assert: a reservation
     * whose `expiresAt` is in the past (a fixed `Clock` or a fixture backdated beyond 10 minutes) is
     * picked up, `StockReleased(reason = RESERVATION_EXPIRED)` is emitted, and the order is
     * cancelled with that reason - the same `assertCancelledEndToEnd` shape as the other causes.
     */
    @Disabled("ReservationExpired scheduler is deferred (Q-07/M-08) - no @Scheduled poller exists yet to drive it")
    @Test
    fun `orderCancelled - reservation expiry releases stock and cancels the order`() {
        // Intentionally left unimplemented; see the KDoc above.
    }

    /** Shared assertion shape for every `orderCancelled` cause: stock released, order cancelled, notification sent. */
    private fun assertCancelledEndToEnd(orderId: String, expectedReason: String, expectedPaymentStatus: PaymentStatus) {
        awaitFlow().untilAsserted {
            val order = orderRepository.findByOrderId(orderId)
            assertThat(order).isNotNull
            assertThat(order!!.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(order.cancellationReason).isEqualTo(expectedReason)

            val reservations = stockReservationRepository.findByOrderId(orderId)
            assertThat(reservations).isNotEmpty
            assertThat(reservations).allSatisfy { assertThat(it.status).isEqualTo(ReservationStatus.RELEASED) }

            val payment = paymentRepository.findByOrderId(orderId)
            assertThat(payment).isNotNull
            assertThat(payment!!.status).isEqualTo(expectedPaymentStatus)

            // sent asynchronously after OrderCancelled, so it is part of the awaited end state
            val notification = notificationRepository.findByOrderIdAndType(orderId, NotificationType.ORDER_CANCELLED)
            assertThat(notification).isNotNull
            assertThat(notification!!.status).isEqualTo(NotificationStatus.SENT)
        }
    }
}
