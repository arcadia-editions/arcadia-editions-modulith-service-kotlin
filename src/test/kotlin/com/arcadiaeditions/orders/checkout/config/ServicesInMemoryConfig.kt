package com.arcadiaeditions.orders.checkout.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*
import com.arcadiaeditions.orders.checkout.events.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.impl.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()
    protected val ordersCheckoutService = OrdersCheckoutServiceImpl(orderRepository(), eventPublisher)

    @Bean
    open fun <T : OrdersCheckoutService> ordersCheckoutService(): T {
        return ordersCheckoutService as T
    }

    companion object {
        var _orders: List<Order>? = null
    }

    fun reloadTestData() {

        val testDataLoader = TestDataLoader(listOf(Order::class.java))
        val orders = _orders ?: testDataLoader.loadCollectionTestDataAsObjects(Order::class.java)
        orderRepository().deleteAll()
        orderRepository().saveAll(orders)
        eventPublisher.getEvents().clear()
    }

    @Bean
    fun eventPublisher(): InMemoryEventPublisher {
        return eventPublisher
    }
}
