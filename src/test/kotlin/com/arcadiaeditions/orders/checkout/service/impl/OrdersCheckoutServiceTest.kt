package com.arcadiaeditions.orders.checkout.service.impl

import com.arcadiaeditions.orders.checkout.config.*
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.repository.jpa.*
import com.arcadiaeditions.orders.checkout.repository.jpa.inmemory.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import com.arcadiaeditions.orders.checkout.service.impl.mappers.*
import java.time.*
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
}
