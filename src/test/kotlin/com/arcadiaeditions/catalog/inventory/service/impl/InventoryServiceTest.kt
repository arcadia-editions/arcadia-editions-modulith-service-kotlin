package com.arcadiaeditions.catalog.inventory.service.impl

import com.arcadiaeditions.catalog.inventory.config.*
import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.StockReleased
import com.arcadiaeditions.catalog.inventory.domain.events.StockReservationConfirmed
import com.arcadiaeditions.catalog.inventory.events.InMemoryEventPublisher
import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.catalog.inventory.service.impl.mappers.*
import com.arcadiaeditions.catalog.inventory.domain.StockReservationTransitions.InvalidStateTransitionException
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// ZenWave DTOs declare quantity fields as the boxed java.lang.Integer type (not kotlin.Int), which
// Kotlin does not autobox into from a plain Int literal (see InventoryServiceImpl.toBoxed()).
private fun Int.i(): Integer = this as Integer

/** Acceptance Test for InventoryService. */
class InventoryServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val inventoryService: InventoryService = context.inventoryService()

    val stockReservationRepository: StockReservationRepositoryInMemory = context.stockReservationRepository()
    val inventoryPositionRepository: InventoryPositionRepositoryInMemory = context.inventoryPositionRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    // --- reserveStock -------------------------------------------------------------------------

    @Test
    fun `reserveStock reserves every line and moves available to reserved (happy path)`() {
        val positionBefore = inventoryPositionRepository.findBySku("AE-PRT-002")!!
        val availableBefore = positionBefore.availableQuantity!!.toInt()
        val reservedBefore = positionBefore.reservedQuantity!!.toInt()

        val result =
            inventoryService.reserveStock(
                ReserveStockInput(orderId = "ORD-NEW-1", items = mutableListOf(ReserveStockItemInput("AE-PRT-002", 3.i())))
            )

        assertEquals(ReserveStockOutcome.RESERVED, result.outcome)
        assertNull(result.unavailable)
        val reserved = result.reserved!!
        assertEquals("ORD-NEW-1", reserved.orderId)
        assertEquals(1, reserved.reservations.size)
        val line = reserved.reservations[0]
        assertEquals("AE-PRT-002", line.sku)
        assertEquals(3, line.quantity!!.toInt())
        assertNotNull(line.reservationId)
        assertEquals(reserved.expiresAt, line.reservedAt!!.plus(InventoryServiceImpl.RESERVATION_TTL))

        val saved = stockReservationRepository.findByOrderId("ORD-NEW-1")
        assertEquals(1, saved.size)
        assertEquals(ReservationStatus.RESERVED, saved[0].status)

        val positionAfter = inventoryPositionRepository.findBySku("AE-PRT-002")!!
        assertEquals(availableBefore - 3, positionAfter.availableQuantity!!.toInt())
        assertEquals(reservedBefore + 3, positionAfter.reservedQuantity!!.toInt())

        // reserveStock itself does not emit an event (ZFL: only confirmStockReservation/releaseStock do).
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    @Test
    fun `reserveStock returns UNAVAILABLE and reserves nothing when a line is short`() {
        // AE-EDN-003 has 0 available (fixture InventoryPosition#3).
        val result =
            inventoryService.reserveStock(
                ReserveStockInput(
                    orderId = "ORD-NEW-2",
                    items =
                        mutableListOf(
                            ReserveStockItemInput("AE-PRT-002", 1.i()),
                            ReserveStockItemInput("AE-EDN-003", 1.i()),
                        ),
                )
            )

        assertEquals(ReserveStockOutcome.UNAVAILABLE, result.outcome)
        assertNull(result.reserved)
        val unavailable = result.unavailable!!
        assertEquals("ORD-NEW-2", unavailable.orderId)
        assertEquals(1, unavailable.unavailableItems.size)
        assertEquals("AE-EDN-003", unavailable.unavailableItems[0].sku)
        assertEquals(0, unavailable.unavailableItems[0].availableQuantity!!.toInt())

        // Nothing reserved for either line, including the one that *was* available.
        assertTrue(stockReservationRepository.findByOrderId("ORD-NEW-2").isEmpty())
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    @Test
    fun `reserveStock treats an unknown sku as unavailable`() {
        val result =
            inventoryService.reserveStock(
                ReserveStockInput(orderId = "ORD-NEW-3", items = mutableListOf(ReserveStockItemInput("NO-SUCH-SKU", 1.i())))
            )

        assertEquals(ReserveStockOutcome.UNAVAILABLE, result.outcome)
        assertEquals(0, result.unavailable!!.unavailableItems[0].availableQuantity!!.toInt())
    }

    @Test
    fun `reserveStock is idempotent on redelivery for the same order`() {
        // ORD-001 already has RES-001/RES-002 (fixtures).
        val before = stockReservationRepository.findByOrderId("ORD-001")

        val result =
            inventoryService.reserveStock(
                ReserveStockInput(orderId = "ORD-001", items = mutableListOf(ReserveStockItemInput("AE-EDN-001", 1.i())))
            )

        assertEquals(ReserveStockOutcome.RESERVED, result.outcome)
        assertEquals(before.size, result.reserved!!.reservations.size)
        // No extra reservations were created and no stock was moved again.
        assertEquals(before.size, stockReservationRepository.findByOrderId("ORD-001").size)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- confirmStockReservation ---------------------------------------------------------------

    @Test
    fun `confirmStockReservation moves reserved to sold and emits one StockReservationConfirmed`() {
        val position = inventoryPositionRepository.findBySku("AE-EDN-001")!!
        val reservedBefore = position.reservedQuantity!!.toInt()
        val soldBefore = position.soldQuantity!!.toInt()

        val result = inventoryService.confirmStockReservation(ConfirmStockReservationInput("ORD-001"))

        assertEquals(2, result.size)
        assertTrue(result.all { it.status == ReservationStatus.CONFIRMED })
        assertTrue(result.all { it.confirmedAt != null })

        val positionAfter = inventoryPositionRepository.findBySku("AE-EDN-001")!!
        assertEquals(reservedBefore - 1, positionAfter.reservedQuantity!!.toInt())
        assertEquals(soldBefore + 1, positionAfter.soldQuantity!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockReservationConfirmed
        assertEquals("ORD-001", event.orderId)
        assertEquals(2, event.items!!.size)
    }

    @Test
    fun `confirmStockReservation on an unknown order throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> {
            inventoryService.confirmStockReservation(ConfirmStockReservationInput("ORD-DOES-NOT-EXIST"))
        }
    }

    @Test
    fun `confirmStockReservation on a released reservation is an invalid transition`() {
        // RES-007/ORD-005 is RELEASED.
        assertThrows<InvalidStateTransitionException> {
            inventoryService.confirmStockReservation(ConfirmStockReservationInput("ORD-005"))
        }
    }

    @Test
    fun `confirmStockReservation is idempotent redelivery for an already confirmed order`() {
        // ORD-002/RES-003 is already CONFIRMED.
        val result = inventoryService.confirmStockReservation(ConfirmStockReservationInput("ORD-002"))

        assertEquals(1, result.size)
        assertEquals(ReservationStatus.CONFIRMED, result[0].status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- releaseStock ---------------------------------------------------------------------------

    @Test
    fun `releaseStock returns quantity to available and emits one StockReleased`() {
        // ORD-004/RES-006 is RESERVED.
        val position = inventoryPositionRepository.findBySku("AE-EDN-001")!!
        val availableBefore = position.availableQuantity!!.toInt()
        val reservedBefore = position.reservedQuantity!!.toInt()

        val result = inventoryService.releaseStock(ReleaseStockInput("ORD-004", StockReleaseReason.PAYMENT_DECLINED))

        assertEquals(1, result.size)
        assertEquals(ReservationStatus.RELEASED, result[0].status)
        assertEquals(StockReleaseReason.PAYMENT_DECLINED, result[0].releaseReason)
        assertNotNull(result[0].releasedAt)

        val positionAfter = inventoryPositionRepository.findBySku("AE-EDN-001")!!
        assertEquals(availableBefore + 1, positionAfter.availableQuantity!!.toInt())
        assertEquals(reservedBefore - 1, positionAfter.reservedQuantity!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockReleased
        assertEquals("ORD-004", event.orderId)
        assertEquals(StockReleaseReason.PAYMENT_DECLINED, event.reason)
        assertEquals(1, event.items!!.size)
    }

    @Test
    fun `releaseStock returns sold quantity to available when the reservation was CONFIRMED`() {
        // ORD-002/RES-003 is CONFIRMED (reserved -> sold already happened).
        val position = inventoryPositionRepository.findBySku("AE-EDN-001")!!
        val availableBefore = position.availableQuantity!!.toInt()
        val soldBefore = position.soldQuantity!!.toInt()

        inventoryService.releaseStock(ReleaseStockInput("ORD-002", StockReleaseReason.PAYMENT_VOIDED))

        val positionAfter = inventoryPositionRepository.findBySku("AE-EDN-001")!!
        assertEquals(availableBefore + 1, positionAfter.availableQuantity!!.toInt())
        assertEquals(soldBefore - 1, positionAfter.soldQuantity!!.toInt())
    }

    @Test
    fun `releaseStock on an unknown order throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> {
            inventoryService.releaseStock(ReleaseStockInput("ORD-DOES-NOT-EXIST", StockReleaseReason.PAYMENT_DECLINED))
        }
    }

    @Test
    fun `releaseStock is idempotent when every reservation of the order is already RELEASED`() {
        // ORD-005/RES-007 is already RELEASED.
        val result = inventoryService.releaseStock(ReleaseStockInput("ORD-005", StockReleaseReason.PAYMENT_DECLINED))

        assertEquals(1, result.size)
        assertEquals(ReservationStatus.RELEASED, result[0].status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }
}
