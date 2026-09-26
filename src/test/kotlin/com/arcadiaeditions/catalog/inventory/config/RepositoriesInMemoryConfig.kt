package com.arcadiaeditions.catalog.inventory.config

import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// @Configuration
open class RepositoriesInMemoryConfig {

    protected val stockReservationRepository = StockReservationRepositoryInMemory()

    @Bean
    @Primary
    fun stockReservationRepository(): StockReservationRepositoryInMemory {
        return stockReservationRepository
    }

    protected val inventoryPositionRepository = InventoryPositionRepositoryInMemory()

    @Bean
    @Primary
    fun inventoryPositionRepository(): InventoryPositionRepositoryInMemory {
        return inventoryPositionRepository
    }
}
