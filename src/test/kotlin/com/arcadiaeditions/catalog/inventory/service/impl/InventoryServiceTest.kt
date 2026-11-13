package com.arcadiaeditions.catalog.inventory.service.impl

import com.arcadiaeditions.catalog.inventory.config.*
import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.catalog.inventory.service.impl.mappers.*
import java.time.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for InventoryService. */
class InventoryServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val inventoryService: InventoryService = context.inventoryService()

    val stockReservationRepository: StockReservationRepositoryInMemory = context.stockReservationRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun servicesUseTheFixedClockTest() {
        org.junit.jupiter.api.Assertions.assertEquals(
            com.arcadiaeditions.config.TestClock.NOW,
            context.clock.instant(),
        )
    }

    private fun position(sku: String) = context.inventoryPositionRepository().findAll().single { it.sku == sku }

    private fun item(sku: String, quantity: Int) = ReserveStockItemInput(sku = sku, quantity = quantity as Integer)

    @Test
    fun reserveStockTest() {
        val before = stockReservationRepository.findAll().size
        val result =
            inventoryService.reserveStock(ReserveStockInput(orderId = "ORD-NEW", items = mutableListOf(item("SKU-001", 2))))

        org.junit.jupiter.api.Assertions.assertEquals(ReserveStockOutcome.RESERVED, result.outcome)
        org.junit.jupiter.api.Assertions.assertNull(result.unavailable)
        val reserved = result.reserved!!
        val expiresAt = com.arcadiaeditions.config.TestClock.NOW.plus(Duration.ofMinutes(10))
        org.junit.jupiter.api.Assertions.assertEquals("ORD-NEW", reserved.orderId)
        org.junit.jupiter.api.Assertions.assertEquals(expiresAt, reserved.expiresAt)
        org.junit.jupiter.api.Assertions.assertEquals(1, reserved.reservations.size)
        val line = reserved.reservations[0]
        org.junit.jupiter.api.Assertions.assertEquals("SKU-001", line.sku)
        org.junit.jupiter.api.Assertions.assertEquals(2, line.quantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, line.reservedAt)
        org.junit.jupiter.api.Assertions.assertNotNull(line.reservationId)

        val saved = stockReservationRepository.findAll().filter { it.orderId == "ORD-NEW" }
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, stockReservationRepository.findAll().size)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.size)
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RESERVED, saved[0].status)
        org.junit.jupiter.api.Assertions.assertEquals(expiresAt, saved[0].expiresAt)
        org.junit.jupiter.api.Assertions.assertEquals(line.reservationId, saved[0].reservationId)
        val position = position("SKU-001")
        org.junit.jupiter.api.Assertions.assertEquals(87, position.availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(8, position.reservedQuantity!!.toInt())
    }

    @Test
    fun reserveStockSeveralLinesTest() {
        val result =
            inventoryService.reserveStock(
                ReserveStockInput(orderId = "ORD-NEW", items = mutableListOf(item("SKU-001", 1), item("SKU-002", 3)))
            )

        org.junit.jupiter.api.Assertions.assertEquals(ReserveStockOutcome.RESERVED, result.outcome)
        org.junit.jupiter.api.Assertions.assertEquals(2, result.reserved!!.reservations.size)
        org.junit.jupiter.api.Assertions.assertEquals(
            2,
            stockReservationRepository.findAll().count { it.orderId == "ORD-NEW" },
        )
        org.junit.jupiter.api.Assertions.assertEquals(88, position("SKU-001").availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(43, position("SKU-002").availableQuantity!!.toInt())
    }

    @Test
    fun reserveStockUnavailableTest() {
        val before = stockReservationRepository.findAll().size
        val result =
            inventoryService.reserveStock(
                ReserveStockInput(orderId = "ORD-NEW", items = mutableListOf(item("SKU-001", 1), item("SKU-003", 2)))
            )

        org.junit.jupiter.api.Assertions.assertEquals(ReserveStockOutcome.UNAVAILABLE, result.outcome)
        org.junit.jupiter.api.Assertions.assertNull(result.reserved)
        val unavailable = result.unavailable!!
        org.junit.jupiter.api.Assertions.assertEquals("ORD-NEW", unavailable.orderId)
        org.junit.jupiter.api.Assertions.assertEquals(1, unavailable.unavailableItems.size)
        val line = unavailable.unavailableItems[0]
        org.junit.jupiter.api.Assertions.assertEquals("SKU-003", line.sku)
        org.junit.jupiter.api.Assertions.assertEquals(2, line.requestedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(0, line.availableQuantity!!.toInt())

        org.junit.jupiter.api.Assertions.assertEquals(before, stockReservationRepository.findAll().size)
        org.junit.jupiter.api.Assertions.assertEquals(89, position("SKU-001").availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(6, position("SKU-001").reservedQuantity!!.toInt())
    }

    private fun releasedEvents() =
        context.eventPublisher().getEvents().filterIsInstance<com.arcadiaeditions.catalog.inventory.domain.events.StockReleased>()

    @Test
    fun releaseStockTest() {
        // C-15: ORD-015 RESERVED x1 (SKU-002 x1), PaymentDeclined
        val result =
            inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-015", reason = StockReleaseReason.PAYMENT_DECLINED))

        org.junit.jupiter.api.Assertions.assertEquals(1, result.size)
        val saved = stockReservationRepository.findAll().single { it.orderId == "ORD-015" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.PAYMENT_DECLINED, saved.releaseReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.confirmedAt)
        // reserved -> available
        val sku2 = position("SKU-002")
        org.junit.jupiter.api.Assertions.assertEquals(47, sku2.availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(1, sku2.reservedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(2, sku2.soldQuantity!!.toInt())
        // exactly one event with the items
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0]
        org.junit.jupiter.api.Assertions.assertEquals("ORD-015", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.PAYMENT_DECLINED, event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(
            listOf("RES-015:SKU-002:1"),
            event.items!!.map { "${it.reservationId}:${it.sku}:${it.quantity}" },
        )
    }

    @Test
    fun releaseStockFromConfirmedTest() {
        // C-18: ORD-018 CONFIRMED x1 (SKU-001 x1), PaymentVoided
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-018", reason = StockReleaseReason.PAYMENT_VOIDED))

        val saved = stockReservationRepository.findAll().single { it.orderId == "ORD-018" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.PAYMENT_VOIDED, saved.releaseReason)
        // A-08: keeps confirmedAt
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T10:56:00Z"), saved.confirmedAt)
        // A-02: sold -> available
        val sku1 = position("SKU-001")
        org.junit.jupiter.api.Assertions.assertEquals(90, sku1.availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(6, sku1.reservedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(4, sku1.soldQuantity!!.toInt())
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.PAYMENT_VOIDED, events[0].reason)
        org.junit.jupiter.api.Assertions.assertEquals("RES-018", events[0].items!!.single().reservationId)
    }

    @Test
    fun releaseStockRedeliveryTest() {
        // C-08: ORD-008 already RELEASED -> nothing changes, no event
        val before = stockReservationRepository.findAll().single { it.orderId == "ORD-008" }.copy()
        val result =
            inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-008", reason = StockReleaseReason.PAYMENT_DECLINED))

        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertTrue(result.all { it.status == ReservationStatus.RELEASED })
        val after = stockReservationRepository.findAll().single { it.orderId == "ORD-008" }
        org.junit.jupiter.api.Assertions.assertEquals(before.releasedAt, after.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(before.releaseReason, after.releaseReason)
        org.junit.jupiter.api.Assertions.assertEquals(89, position("SKU-001").availableQuantity!!.toInt())
    }

    @Test
    fun releaseStockTwiceTest() {
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-015", reason = StockReleaseReason.PAYMENT_DECLINED))
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-015", reason = StockReleaseReason.PAYMENT_DECLINED))

        org.junit.jupiter.api.Assertions.assertEquals(1, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(47, position("SKU-002").availableQuantity!!.toInt())
    }

    @Test
    fun releaseStockReasonFromPaymentEventTest() {
        val mapper =
            com.arcadiaeditions.catalog.inventory.events.processing.mappers.PaymentsProcessingEventsListenerMapper.INSTANCE
        val declined =
            mapper.asReleaseStockInput(
                com.arcadiaeditions.payments.processing.domain.event.PaymentDeclined(orderId = "ORD-015", reason = "card_declined")
            )
        org.junit.jupiter.api.Assertions.assertEquals(ReleaseStockInput("ORD-015", StockReleaseReason.PAYMENT_DECLINED), declined)
        val exhausted =
            mapper.asReleaseStockInput(
                com.arcadiaeditions.payments.processing.domain.event.PaymentRetryExhausted(orderId = "ORD-016", reason = "processor_timeout")
            )
        org.junit.jupiter.api.Assertions.assertEquals(
            ReleaseStockInput("ORD-016", StockReleaseReason.PAYMENT_RETRY_EXHAUSTED),
            exhausted,
        )
        val voided =
            mapper.asReleaseStockInput(
                com.arcadiaeditions.payments.processing.domain.event.PaymentVoided(orderId = "ORD-018", reason = "fulfillment_failed")
            )
        org.junit.jupiter.api.Assertions.assertEquals(ReleaseStockInput("ORD-018", StockReleaseReason.PAYMENT_VOIDED), voided)
    }

    private fun confirmedEvents() =
        context.eventPublisher().getEvents().filterIsInstance<com.arcadiaeditions.catalog.inventory.domain.events.StockReservationConfirmed>()

    @Test
    fun confirmStockReservationTest() {
        // C-02: ORD-002 has two RESERVED reservations (SKU-001 x2, SKU-002 x1)
        val result = inventoryService.confirmStockReservation(ConfirmStockReservationInput(orderId = "ORD-002"))

        org.junit.jupiter.api.Assertions.assertEquals(2, result.size)
        val saved = stockReservationRepository.findAll().filter { it.orderId == "ORD-002" }
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.size)
        saved.forEach {
            org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.CONFIRMED, it.status)
            org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, it.confirmedAt)
        }
        // reserved -> sold
        val sku1 = position("SKU-001")
        org.junit.jupiter.api.Assertions.assertEquals(4, sku1.reservedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(7, sku1.soldQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(89, sku1.availableQuantity!!.toInt())
        val sku2 = position("SKU-002")
        org.junit.jupiter.api.Assertions.assertEquals(1, sku2.reservedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(3, sku2.soldQuantity!!.toInt())
        // exactly one event with the items
        val events = confirmedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0]
        org.junit.jupiter.api.Assertions.assertEquals("ORD-002", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.confirmedAt)
        org.junit.jupiter.api.Assertions.assertEquals(
            setOf("RES-002:SKU-001:2", "RES-003:SKU-002:1"),
            event.items!!.map { "${it.reservationId}:${it.sku}:${it.quantity}" }.toSet(),
        )
        org.junit.jupiter.api.Assertions.assertEquals(2, event.items!!.size)
    }

    @Test
    fun confirmStockReservationRedeliveryTest() {
        // C-03: ORD-003 already CONFIRMED -> nothing changes, no event
        val before = stockReservationRepository.findAll().single { it.orderId == "ORD-003" }.copy()
        val result = inventoryService.confirmStockReservation(ConfirmStockReservationInput(orderId = "ORD-003"))

        org.junit.jupiter.api.Assertions.assertEquals(0, confirmedEvents().size)
        org.junit.jupiter.api.Assertions.assertTrue(result.none { it.status != ReservationStatus.CONFIRMED })
        val after = stockReservationRepository.findAll().single { it.orderId == "ORD-003" }
        org.junit.jupiter.api.Assertions.assertEquals(before.confirmedAt, after.confirmedAt)
        org.junit.jupiter.api.Assertions.assertEquals(5, position("SKU-001").soldQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(6, position("SKU-001").reservedQuantity!!.toInt())
    }

    @Test
    fun confirmStockReservationTwiceTest() {
        inventoryService.confirmStockReservation(ConfirmStockReservationInput(orderId = "ORD-002"))
        inventoryService.confirmStockReservation(ConfirmStockReservationInput(orderId = "ORD-002"))

        org.junit.jupiter.api.Assertions.assertEquals(1, confirmedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(7, position("SKU-001").soldQuantity!!.toInt())
    }
}
