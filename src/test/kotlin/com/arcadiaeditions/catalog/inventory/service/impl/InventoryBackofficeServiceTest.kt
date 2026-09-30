package com.arcadiaeditions.catalog.inventory.service.impl

import com.arcadiaeditions.catalog.inventory.config.*
import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.StockAdjusted
import com.arcadiaeditions.catalog.inventory.domain.events.StockReceived
import com.arcadiaeditions.catalog.inventory.events.InMemoryEventPublisher
import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.catalog.inventory.service.impl.mappers.*
import com.arcadiaeditions.common.DomainRejectionException
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private fun Int.i(): Integer = this as Integer

/** Acceptance Test for InventoryBackofficeService. */
class InventoryBackofficeServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val inventoryBackofficeService: InventoryBackofficeService = context.inventoryBackofficeService()

    val inventoryPositionRepository: InventoryPositionRepositoryInMemory = context.inventoryPositionRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    // --- createInventoryPosition ---------------------------------------------------------------

    @Test
    fun `createInventoryPosition sets initial quantities and saves (happy path)`() {
        val input = CreateInventoryPositionInput(sku = "AE-NEW-SKU", initialQuantity = 100.i())

        val position = inventoryBackofficeService.createInventoryPosition(input)

        assertNotNull(position.id)
        assertEquals("AE-NEW-SKU", position.sku)
        assertEquals(100, position.availableQuantity!!.toInt())
        assertEquals(0, position.reservedQuantity!!.toInt())
        assertEquals(0, position.soldQuantity!!.toInt())
        assertEquals(position, inventoryPositionRepository.findBySku("AE-NEW-SKU"))
    }

    @Test
    fun `createInventoryPosition rejects duplicate sku with 409`() {
        // AE-PRT-002 already exists in fixtures
        val input = CreateInventoryPositionInput(sku = "AE-PRT-002", initialQuantity = 50.i())

        val exception = assertThrows<DomainRejectionException> {
            inventoryBackofficeService.createInventoryPosition(input)
        }
        assertEquals("InventoryPosition already exists for sku: AE-PRT-002", exception.message)
    }

    // --- receiveStock ---------------------------------------------------------------

    @Test
    fun `receiveStock increases available quantity and emits one StockReceived event (happy path)`() {
        val position = inventoryPositionRepository.findBySku("AE-PRT-002")!!
        val availableBefore = position.availableQuantity!!.toInt()

        val input = ReceiveStockInput(sku = "AE-PRT-002", quantity = 10.i(), reason = "Resupply", reference = "PO-123")

        val result = inventoryBackofficeService.receiveStock(input)

        assertEquals(availableBefore + 10, result.availableQuantity!!.toInt())
        assertEquals(inventoryPositionRepository.findBySku("AE-PRT-002")!!.availableQuantity!!.toInt(), result.availableQuantity!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockReceived
        assertEquals("AE-PRT-002", event.sku)
        assertEquals(10.i(), event.quantity)
        assertEquals(availableBefore + 10, event.availableQuantity!!.toInt())
        assertEquals("Resupply", event.reason)
        assertEquals("PO-123", event.reference)
        assertNotNull(event.receivedAt)
    }

    @Test
    fun `receiveStock throws 404 when sku not found`() {
        val input = ReceiveStockInput(sku = "NO-SUCH-SKU", quantity = 10.i(), reason = null, reference = null)

        val exception = assertThrows<NoSuchElementException> {
            inventoryBackofficeService.receiveStock(input)
        }
        assertTrue(exception.message!!.contains("NO-SUCH-SKU"))
    }

    // --- adjustStock ---------------------------------------------------------------

    @Test
    fun `adjustStock adjusts available quantity and emits one StockAdjusted event (happy path)`() {
        val position = inventoryPositionRepository.findBySku("AE-PRT-002")!!
        val availableBefore = position.availableQuantity!!.toInt()

        val input = AdjustStockInput(sku = "AE-PRT-002", quantityDelta = 5.i(), reason = StockAdjustmentReason.FOUND, reference = "Found in warehouse")

        val result = inventoryBackofficeService.adjustStock(input)

        assertEquals(availableBefore + 5, result.availableQuantity!!.toInt())
        assertEquals(inventoryPositionRepository.findBySku("AE-PRT-002")!!.availableQuantity!!.toInt(), result.availableQuantity!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockAdjusted
        assertEquals("AE-PRT-002", event.sku)
        assertEquals(5.i(), event.quantityDelta)
        assertEquals(availableBefore + 5, event.availableQuantity!!.toInt())
        assertEquals(StockAdjustmentReason.FOUND, event.reason)
        assertEquals("Found in warehouse", event.reference)
        assertNotNull(event.adjustedAt)
    }

    @Test
    fun `adjustStock can reduce available quantity (negative delta)`() {
        val position = inventoryPositionRepository.findBySku("AE-PRT-002")!!
        val availableBefore = position.availableQuantity!!.toInt()

        val input = AdjustStockInput(sku = "AE-PRT-002", quantityDelta = (-3).i(), reason = StockAdjustmentReason.DAMAGED, reference = "Damaged unit removed")

        val result = inventoryBackofficeService.adjustStock(input)

        assertEquals(availableBefore - 3, result.availableQuantity!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockAdjusted
        assertEquals((-3).i(), event.quantityDelta)
    }

    @Test
    fun `adjustStock rejects negative result with 409`() {
        // AE-PRT-002 has 247 available; try to adjust by -300 to get negative result
        val input = AdjustStockInput(sku = "AE-PRT-002", quantityDelta = (-300).i(), reason = StockAdjustmentReason.LOST, reference = "Lost in shipping")

        val exception = assertThrows<DomainRejectionException> {
            inventoryBackofficeService.adjustStock(input)
        }
        assertTrue(exception.message!!.contains("negative"))
        assertTrue(exception.message!!.contains("AE-PRT-002"))
    }

    @Test
    fun `adjustStock throws 404 when sku not found`() {
        val input = AdjustStockInput(sku = "NO-SUCH-SKU", quantityDelta = 5.i(), reason = StockAdjustmentReason.FOUND, reference = null)

        val exception = assertThrows<NoSuchElementException> {
            inventoryBackofficeService.adjustStock(input)
        }
        assertTrue(exception.message!!.contains("NO-SUCH-SKU"))
    }
}
