package com.arcadiaeditions.orders.checkout.adapters.web

import com.arcadiaeditions.orders.checkout.adapters.web.*
import com.arcadiaeditions.orders.checkout.adapters.web.dtos.*
import com.arcadiaeditions.orders.checkout.config.ServicesInMemoryConfig

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


}
