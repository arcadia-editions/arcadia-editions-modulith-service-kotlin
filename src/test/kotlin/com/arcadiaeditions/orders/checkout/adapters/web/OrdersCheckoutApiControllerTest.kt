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
        val reqBody: StartOrderCheckoutInputDTO = StartOrderCheckoutInputDTO(customerId = "aaa", customerEmail = "aaa", paymentMethodId = "aaa", currency = "aaa", items = mutableListOf(), shippingAddress = ShippingAddressInputDTO(name = "aaa", line1 = "aaa", city = "aaa", postalCode = "aaa", country = "aaa"))
        val response = controller.startOrderCheckout(reqBody)
        Assertions.assertEquals(201, response.statusCode.value())
    }

    @Test
    fun getOrderTest() {
        val id: Long = 0L
        val response = controller.getOrder(id)
        Assertions.assertEquals(200, response.statusCode.value())
    }


}
