package com.arcadiaeditions.orders.checkout.config

import com.arcadiaeditions.catalog.inventory.service.InventoryService
import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*
import com.arcadiaeditions.orders.checkout.events.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.impl.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import com.arcadiaeditions.catalog.inventory.config.ServicesInMemoryConfig as CatalogInventoryServicesInMemoryConfig

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()

    // X-06: fixed clock so tests can assert on exact timestamps.
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    // G-OC-01/X-04/G-FT-04: startOrderCheckout/confirmOrder call CatalogInventory only through its
    // InventoryService interface (never its repository/impl). In-memory tests wire the real
    // in-memory InventoryServiceImpl behind that interface so reserveStock's RESERVED/UNAVAILABLE
    // outcome is exercised against the same InventoryPosition/StockReservation fixtures as
    // InventoryServiceTest.
    protected val catalogInventoryServicesInMemoryConfig = CatalogInventoryServicesInMemoryConfig()
    protected val inventoryService: InventoryService = catalogInventoryServicesInMemoryConfig.inventoryService()

    protected val ordersCheckoutService =
        OrdersCheckoutServiceImpl(orderRepository(), eventPublisher, clock, inventoryService)

    @Bean open fun clock(): Clock = clock

    @Bean
    open fun <T : OrdersCheckoutService> ordersCheckoutService(): T {
        return ordersCheckoutService as T
    }

    companion object {
        var _orders: List<Order>? = null
    }

    fun reloadTestData() {

        catalogInventoryServicesInMemoryConfig.reloadTestData()

        val testDataLoader =
            TestDataLoader(listOf(Order::class.java, OrderItem::class.java, ShippingAddress::class.java))
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
