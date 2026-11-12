package com.arcadiaeditions.catalog.inventory.repository.jpa

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import jakarta.persistence.EntityManager
import java.time.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class InventoryPositionRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var inventoryPositionRepository: InventoryPositionRepository

    @Test
    fun findAllTest() {
        val results = inventoryPositionRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val inventoryPosition =
            inventoryPositionRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(inventoryPosition.id)
        Assertions.assertNotNull(inventoryPosition.version)
    }

    @Test
    fun saveTest() {
        val inventoryPosition = InventoryPosition()
        inventoryPosition.sku = ""
        inventoryPosition.availableQuantity = 0 as Integer
        inventoryPosition.reservedQuantity = 0 as Integer
        inventoryPosition.soldQuantity = 0 as Integer

        // Persist aggregate root
        val created = inventoryPositionRepository.save(inventoryPosition)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val inventoryPosition =
            inventoryPositionRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        inventoryPosition.sku = ""
        inventoryPosition.availableQuantity = 0 as Integer
        inventoryPosition.reservedQuantity = 0 as Integer
        inventoryPosition.soldQuantity = 0 as Integer

        val updated = inventoryPositionRepository.save(inventoryPosition)
        Assertions.assertEquals("", updated.sku)
        Assertions.assertEquals(0 as Integer, updated.availableQuantity)
        Assertions.assertEquals(0 as Integer, updated.reservedQuantity)
        Assertions.assertEquals(0 as Integer, updated.soldQuantity)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        inventoryPositionRepository.deleteById(id)
        val notFound = inventoryPositionRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
