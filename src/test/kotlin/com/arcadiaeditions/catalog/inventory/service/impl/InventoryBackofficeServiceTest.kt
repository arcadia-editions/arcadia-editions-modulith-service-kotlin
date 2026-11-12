package com.arcadiaeditions.catalog.inventory.service.impl

import com.arcadiaeditions.catalog.inventory.config.*
import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.catalog.inventory.service.impl.mappers.*
import java.time.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for InventoryBackofficeService. */
class InventoryBackofficeServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val inventoryBackofficeService: InventoryBackofficeService = context.inventoryBackofficeService()

    val inventoryPositionRepository: InventoryPositionRepositoryInMemory = context.inventoryPositionRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun createInventoryPositionTest() {
        val input: CreateInventoryPositionInput = CreateInventoryPositionInput(sku = "", initialQuantity = 0 as Integer)
        val inventoryPosition = inventoryBackofficeService.createInventoryPosition(input)
        assertNotNull(inventoryPosition.id)
        assertTrue(inventoryPositionRepository.containsEntity(inventoryPosition))
    }

    @Test
    fun receiveStockTest() { // TODO: implement this test
    }

    @Test
    fun adjustStockTest() { // TODO: implement this test
    }
}
