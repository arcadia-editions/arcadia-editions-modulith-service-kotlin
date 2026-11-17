package com.arcadiaeditions.orders.checkout.adapters.web

import com.arcadiaeditions.orders.checkout.adapters.web.*
import com.arcadiaeditions.orders.checkout.adapters.web.dtos.*
import com.arcadiaeditions.orders.checkout.config.ServicesInMemoryConfig
import com.arcadiaeditions.orders.checkout.core.inbound.StockUnavailableException

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.*
import java.time.*
import java.util.*

/**
 * Test controller for OrdersCheckoutApiController.
 */
class OrdersCheckoutApiControllerTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val context = ServicesInMemoryConfig()

    private val controller = OrdersCheckoutApiController( context.ordersCheckoutService() )

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }


    @Test
    fun startOrderCheckoutTest() {
        val reqBody = StartOrderCheckoutInputDTO(
            customerId = "CUST-001", customerEmail = "customer01@example.com", paymentMethodId = "pm_card_visa",
            currency = "EUR",
            items = mutableListOf(OrderItemInputDTO(sku = "SKU-001", quantity = 1, unitPrice = 49.99)),
            shippingAddress = ShippingAddressInputDTO(name = "Alex Example", line1 = "Calle Ficticia 1", city = "Madrid", postalCode = "28001", country = "ES"),
        )
        val response = controller.startOrderCheckout(reqBody)
        Assertions.assertEquals(201, response.statusCode.value())
        val body = response.body!!
        Assertions.assertNotNull(body.orderId)
        Assertions.assertEquals("CREATED", body.status.toString())
        Assertions.assertEquals(49.99, body.totalAmount!!.toDouble(), 0.001)
    }

    // C-07: SKU-003 has no stock; P-03 A: 409 with StockUnavailable body, no Order
    @Test
    fun startOrderCheckoutStockUnavailableTest() {
        val reqBody = StartOrderCheckoutInputDTO(
            customerId = "CUST-007", customerEmail = "customer07@example.com", paymentMethodId = "pm_card_visa",
            currency = "EUR",
            items = mutableListOf(OrderItemInputDTO(sku = "SKU-003", quantity = 1, unitPrice = 75.00)),
            shippingAddress = ShippingAddressInputDTO(name = "Alex Example", line1 = "Calle Ficticia 1", city = "Madrid", postalCode = "28001", country = "ES"),
        )
        val ex = Assertions.assertThrows(StockUnavailableException::class.java) { controller.startOrderCheckout(reqBody) }
        val response = OrdersCheckoutApiExceptionHandler().handleStockUnavailable(ex)
        Assertions.assertEquals(409, response.statusCode.value())
        val body = response.body!!
        Assertions.assertTrue(body.orderId!!.startsWith("ORD-"))
        Assertions.assertEquals(1, body.unavailableItems!!.size)
        Assertions.assertEquals("SKU-003", body.unavailableItems!![0].sku)
        Assertions.assertEquals(1, body.unavailableItems!![0].requestedQuantity)
        Assertions.assertEquals(0, body.unavailableItems!![0].availableQuantity)
    }

    @Test
    fun getOrderTest() {
        val response = controller.getOrder(1L)
        Assertions.assertEquals(200, response.statusCode.value())
        Assertions.assertEquals("ORD-001", response.body!!.orderId)
    }

    @Test
    fun getOrderNotFoundTest() {
        val response = controller.getOrder(9999L)
        Assertions.assertEquals(404, response.statusCode.value())
    }

    // T-74: ZDL bounds quantity min(1), unitPrice min(0): the @Valid of the API answers 400 before the controller runs
    private fun inputWith(quantity: Int, unitPrice: Double) = StartOrderCheckoutInputDTO(
        customerId = "CUST-001", customerEmail = "customer01@example.com", paymentMethodId = "pm_card_visa",
        currency = "EUR",
        items = mutableListOf(OrderItemInputDTO(sku = "SKU-001", quantity = quantity, unitPrice = unitPrice)),
        shippingAddress = ShippingAddressInputDTO(name = "Alex Example", line1 = "Calle Ficticia 1", city = "Madrid", postalCode = "28001", country = "ES"),
    )

    private fun violations(dto: StartOrderCheckoutInputDTO): Set<String> =
        jakarta.validation.Validation.buildDefaultValidatorFactory().use { f ->
            f.validator.validate(dto).map { it.propertyPath.toString() }.toSet()
        }

    @Test
    fun startOrderCheckoutQuantityBelowOneIsInvalidTest() {
        Assertions.assertEquals(setOf("items[0].quantity"), violations(inputWith(0, 10.0)))
    }

    @Test
    fun startOrderCheckoutNegativeUnitPriceIsInvalidTest() {
        Assertions.assertEquals(setOf("items[0].unitPrice"), violations(inputWith(1, -0.01)))
    }

    @Test
    fun startOrderCheckoutBoundsAreValidTest() {
        Assertions.assertTrue(violations(inputWith(1, 0.0)).isEmpty())
    }

}
