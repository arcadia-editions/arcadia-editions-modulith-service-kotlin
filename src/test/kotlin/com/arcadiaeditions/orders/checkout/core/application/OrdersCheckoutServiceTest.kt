package com.arcadiaeditions.orders.checkout.core.application

import com.arcadiaeditions.orders.checkout.config.*
import com.arcadiaeditions.orders.checkout.core.application.mappers.*
import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*
import com.arcadiaeditions.orders.checkout.infrastructure.jpa.inmemory.*
import java.time.*
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun startOrderCheckoutTest() { // TODO: implement this test
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
