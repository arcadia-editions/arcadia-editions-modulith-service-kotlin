package com.arcadiaeditions.catalog.inventory.config

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.catalog.inventory.events.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.impl.*
import com.arcadiaeditions.config.TestDataLoader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()
    protected val inventoryService = InventoryServiceImpl(stockReservationRepository(), eventPublisher)
    protected val inventoryBackofficeService =
        InventoryBackofficeServiceImpl(inventoryPositionRepository(), eventPublisher)

    @Bean
    open fun <T : InventoryService> inventoryService(): T {
        return inventoryService as T
    }

    @Bean
    open fun <T : InventoryBackofficeService> inventoryBackofficeService(): T {
        return inventoryBackofficeService as T
    }

    companion object {
        var _stockReservations: List<StockReservation>? = null
        var _inventoryPositions: List<InventoryPosition>? = null
    }

    fun reloadTestData() {

        val testDataLoader = TestDataLoader(listOf(StockReservation::class.java, InventoryPosition::class.java))
        val stockReservations =
            _stockReservations ?: testDataLoader.loadCollectionTestDataAsObjects(StockReservation::class.java)
        stockReservationRepository().deleteAll()
        stockReservationRepository().saveAll(stockReservations)
        val inventoryPositions =
            _inventoryPositions ?: testDataLoader.loadCollectionTestDataAsObjects(InventoryPosition::class.java)
        inventoryPositionRepository().deleteAll()
        inventoryPositionRepository().saveAll(inventoryPositions)
        eventPublisher.getEvents().clear()
    }

    @Bean
    fun eventPublisher(): InMemoryEventPublisher {
        return eventPublisher
    }
}
