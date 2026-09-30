package com.arcadiaeditions.catalog.inventory.config

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.catalog.inventory.events.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.impl.*
import com.arcadiaeditions.config.TestDataLoader
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()

    // X-06: fixed clock so tests can assert on exact timestamps.
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    protected val inventoryService =
        InventoryServiceImpl(stockReservationRepository(), inventoryPositionRepository(), eventPublisher, clock)
    protected val inventoryBackofficeService =
        InventoryBackofficeServiceImpl(inventoryPositionRepository(), eventPublisher, clock)

    @Bean open fun clock(): Clock = clock

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
