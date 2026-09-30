package com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.InventoryPositionRepository
import java.math.*
import java.time.*
import java.util.*

class InventoryPositionRepositoryInMemory :
    InMemoryJpaRepository<InventoryPosition, Long>(), InventoryPositionRepository {

    override fun findBySku(sku: String): InventoryPosition? = findByUniqueField("sku", sku)
}
