package com.arcadiaeditions.orders.checkout.service.impl

import com.arcadiaeditions.orders.checkout.config.*
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.OrderTransitions.InvalidStateTransitionException
import com.arcadiaeditions.orders.checkout.domain.events.OrderCancelled
import com.arcadiaeditions.orders.checkout.domain.events.OrderConfirmed
import com.arcadiaeditions.orders.checkout.domain.events.OrderCreated
import com.arcadiaeditions.orders.checkout.domain.events.StockUnavailable
import com.arcadiaeditions.orders.checkout.events.InMemoryEventPublisher
import com.arcadiaeditions.orders.checkout.repository.jpa.*
import com.arcadiaeditions.orders.checkout.repository.jpa.inmemory.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.StockUnavailableException
import com.arcadiaeditions.orders.checkout.service.dtos.*
import com.arcadiaeditions.orders.checkout.service.impl.mappers.*
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// ZenWave DTOs declare quantity fields as the boxed java.lang.Integer type (not kotlin.Int), which
// Kotlin does not autobox into from a plain Int literal (see InventoryServiceImpl.toBoxed()).
private fun Int.i(): Integer = this as Integer

/** Acceptance Test for OrdersCheckoutService. */
class OrdersCheckoutServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val ordersCheckoutService: OrdersCheckoutService = context.ordersCheckoutService()

    val orderRepository: OrderRepositoryInMemory = context.orderRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    private fun shippingAddress() =
        ShippingAddressInput(
            name = "Ada Lovelace",
            line1 = "12 Marylebone Rd",
            line2 = null,
            city = "London",
            region = null,
            postalCode = "NW1 5LR",
            country = "GB",
        )

    // --- startOrderCheckout ---------------------------------------------------------------------

    @Test
    fun `startOrderCheckout reserves stock, creates the order and emits only OrderCreated`() {
        val input =
            StartOrderCheckoutInput(
                customerId = "CUST-999",
                customerEmail = "new@example.com",
                paymentMethodId = "pm_card_visa",
                currency = "EUR",
                items = mutableListOf(OrderItemInput("AE-PRT-002", 2.i(), BigDecimal("25.00"))),
                shippingAddress = shippingAddress(),
            )

        val order = ordersCheckoutService.startOrderCheckout(input)

        assertEquals(OrderStatus.CREATED, order.status)
        assertNotNull(order.orderId)
        assertNotNull(order.createdAt)
        assertNull(order.confirmedAt)
        assertNull(order.cancelledAt)
        assertEquals(BigDecimal("50.00"), order.totalAmount)
        assertEquals(order, orderRepository.findByOrderId(order.orderId!!))

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as OrderCreated
        assertEquals(order.orderId, event.orderId)
        assertEquals(BigDecimal("50.00"), event.amount)
    }

    @Test
    fun `startOrderCheckout emits StockUnavailable and throws without persisting an order when stock is short`() {
        // AE-EDN-003 has 0 available (fixture InventoryPosition#3).
        val input =
            StartOrderCheckoutInput(
                customerId = "CUST-999",
                customerEmail = "new@example.com",
                paymentMethodId = "pm_card_visa",
                currency = "EUR",
                items = mutableListOf(OrderItemInput("AE-EDN-003", 1.i(), BigDecimal("79.00"))),
                shippingAddress = shippingAddress(),
            )

        assertThrows<StockUnavailableException> { ordersCheckoutService.startOrderCheckout(input) }

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockUnavailable
        assertEquals("CUST-999", event.customerId)
        assertEquals(1, event.unavailableItems!!.size)
        assertEquals("AE-EDN-003", event.unavailableItems!![0].sku)

        // Q-03/Q-15: no order record was created for this order id.
        assertTrue(orderRepository.findAll().none { it.customerId == "CUST-999" })
    }

    // --- confirmOrder ----------------------------------------------------------------------------

    @Test
    fun `confirmOrder confirms the stock reservation and moves the order to CONFIRMED`() {
        // ORD-001 is CREATED with RES-001/RES-002 RESERVED.
        val order = ordersCheckoutService.confirmOrder(ConfirmOrderInput("ORD-001"))

        assertEquals(OrderStatus.CONFIRMED, order.status)
        assertNotNull(order.confirmedAt)
        assertEquals(order, orderRepository.findByOrderId("ORD-001"))

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as OrderConfirmed
        assertEquals("ORD-001", event.orderId)
    }

    @Test
    fun `confirmOrder on an unknown order throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> { ordersCheckoutService.confirmOrder(ConfirmOrderInput("ORD-NOPE")) }
    }

    @Test
    fun `confirmOrder on a cancelled order is an invalid transition`() {
        // ORD-005 is CANCELLED.
        assertThrows<InvalidStateTransitionException> { ordersCheckoutService.confirmOrder(ConfirmOrderInput("ORD-005")) }
    }

    @Test
    fun `confirmOrder is idempotent redelivery for an already confirmed order`() {
        // ORD-002 is already CONFIRMED.
        val order = ordersCheckoutService.confirmOrder(ConfirmOrderInput("ORD-002"))

        assertEquals(OrderStatus.CONFIRMED, order.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- cancelOrder -----------------------------------------------------------------------------

    @Test
    fun `cancelOrder cancels a CREATED order and emits OrderCancelled`() {
        // ORD-004 is CREATED.
        val order = ordersCheckoutService.cancelOrder(CancelOrderInput("ORD-004", "PAYMENT_RETRY_EXHAUSTED"))

        assertEquals(OrderStatus.CANCELLED, order.status)
        assertNotNull(order.cancelledAt)
        assertEquals("PAYMENT_RETRY_EXHAUSTED", order.cancellationReason)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as OrderCancelled
        assertEquals("ORD-004", event.orderId)
        assertEquals("PAYMENT_RETRY_EXHAUSTED", event.reason)
    }

    @Test
    fun `cancelOrder cancels a CONFIRMED order too`() {
        // ORD-002 is CONFIRMED.
        val order = ordersCheckoutService.cancelOrder(CancelOrderInput("ORD-002", "PAYMENT_VOIDED"))
        assertEquals(OrderStatus.CANCELLED, order.status)
    }

    @Test
    fun `cancelOrder on an unknown order throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> { ordersCheckoutService.cancelOrder(CancelOrderInput("ORD-NOPE", "x")) }
    }

    @Test
    fun `cancelOrder is idempotent no-op for an already cancelled order`() {
        // ORD-005 is already CANCELLED.
        val order = ordersCheckoutService.cancelOrder(CancelOrderInput("ORD-005", "PAYMENT_DECLINED"))

        assertEquals(OrderStatus.CANCELLED, order.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }
}
