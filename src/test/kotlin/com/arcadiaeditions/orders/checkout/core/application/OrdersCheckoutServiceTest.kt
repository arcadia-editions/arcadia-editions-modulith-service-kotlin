package com.arcadiaeditions.orders.checkout.core.application

import com.arcadiaeditions.orders.checkout.config.*
import com.arcadiaeditions.orders.checkout.core.application.mappers.*
import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.*
import com.arcadiaeditions.config.TestClock
import com.arcadiaeditions.orders.checkout.core.domain.events.OrderCreated
import com.arcadiaeditions.orders.checkout.infrastructure.events.InMemoryEventPublisher
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for OrdersCheckoutService. */
class OrdersCheckoutServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val ordersCheckoutService: OrdersCheckoutService = context.ordersCheckoutService()

    val orderRepository: OrderRepositoryInMemory = context.orderRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun servicesUseTheFixedClockTest() {
        org.junit.jupiter.api.Assertions.assertEquals(
            com.arcadiaeditions.config.TestClock.NOW,
            context.clock.instant(),
        )
    }

    @Test
    fun startOrderCheckoutTest() {
        val input =
            StartOrderCheckoutInput(
                customerId = "CUST-001",
                customerEmail = "customer01@example.com",
                paymentMethodId = "pm_card_visa",
                currency = "EUR",
                items =
                    mutableListOf(
                        OrderItemInput("SKU-001", 2 as Integer, BigDecimal("49.99")),
                        OrderItemInput("SKU-001", 1 as Integer, BigDecimal("10.00")),
                    ),
                shippingAddress =
                    ShippingAddressInput(
                        name = "Alex Example",
                        line1 = "Calle Ficticia 1",
                        city = "Madrid",
                        postalCode = "28001",
                        country = "ES",
                        line2 = null,
                        region = null,
                    ),
            )
        val before = orderRepository.count()

        val order = ordersCheckoutService.startOrderCheckout(input)

        assertEquals(before + 1, orderRepository.count())
        assertNotNull(order.id)
        assertTrue(order.orderId!!.isNotBlank())
        assertEquals(OrderStatus.CREATED, order.status)
        assertEquals(0, BigDecimal("109.98").compareTo(order.totalAmount))
        assertEquals(TestClock.NOW, order.createdAt)
        assertEquals(2, order.items.size)
        assertEquals("CUST-001", order.customerId)
        assertEquals("Madrid", order.shippingAddress!!.city)

        val events = (context.eventPublisher() as InMemoryEventPublisher).getEvents()
        assertEquals(1, events.size)
        val created = events[0] as OrderCreated
        assertEquals(order.orderId, created.orderId)
        assertEquals(0, BigDecimal("109.98").compareTo(created.amount))
        assertEquals("EUR", created.currency)
        assertEquals("pm_card_visa", created.paymentMethodId)
        assertEquals("customer01@example.com", created.customerEmail)

        // stock reserved in-process for the generated orderId
        val position = context.inventoryContext.inventoryPositionRepository().findAll().first { it.sku == "SKU-001" }
        assertEquals(86, position.availableQuantity!!.toInt())
        assertEquals(9, position.reservedQuantity!!.toInt())
    }

    @Test
    fun confirmOrderTest() { // TODO: implement this test
    }

    @Test
    fun cancelOrderTest() { // TODO: implement this test
    }

    @Test
    fun getOrderTest() {
        val id: Long = 1L
        val order = ordersCheckoutService.getOrder(id)
        assertNotNull(order)
    }
}
