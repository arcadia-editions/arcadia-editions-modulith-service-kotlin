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

    @Test
    fun reservationExpiredJobIsGatedByPropertyTest() {
        val gate =
            com.arcadiaeditions.catalog.inventory.events.scheduler.ReservationExpiredJob::class.java.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty::class.java
            )
        org.junit.jupiter.api.Assertions.assertNotNull(gate)
        org.junit.jupiter.api.Assertions.assertEquals(
            "catalog-inventory.reservation-expired-job.enabled",
            gate.name.single(),
        )
        org.junit.jupiter.api.Assertions.assertTrue(gate.matchIfMissing)
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

    @Test
    fun releaseStockExpiryOverConfirmedOrderDoesNothingTest() {
        // C-03: ORD-003 CONFIRMED (expiresAt 11:40 <= now): expiry never releases CONFIRMED lines, no event
        val before = stockReservationRepository.findAll().single { it.orderId == "ORD-003" }.copy()
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-003", reason = StockReleaseReason.RESERVATION_EXPIRED))

        val after = stockReservationRepository.findAll().single { it.orderId == "ORD-003" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.CONFIRMED, after.status)
        org.junit.jupiter.api.Assertions.assertNull(after.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(before.confirmedAt, after.confirmedAt)
        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(5, position("SKU-001").soldQuantity!!.toInt())
    }

    @Test
    fun releaseStockExpiryBeforeDeadlineDoesNothingTest() {
        // C-01: ORD-001 RESERVED, expiresAt 12:08 > now 12:00 -> nothing, no event
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-001", reason = StockReleaseReason.RESERVATION_EXPIRED))

        val after = stockReservationRepository.findAll().single { it.orderId == "ORD-001" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RESERVED, after.status)
        org.junit.jupiter.api.Assertions.assertNull(after.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
    }

    private fun reservationExpiredJob(clock: Clock = context.clock) =
        com.arcadiaeditions.catalog.inventory.events.scheduler.ReservationExpiredJob(
            stockReservationRepository,
            // the service re-checks the deadline with its own clock: same clock as the job
            InventoryServiceImpl(stockReservationRepository, context.inventoryPositionRepository(), context.eventPublisher(), clock),
            clock,
        )

    @Test
    fun reservationExpiredJobTest() {
        // C-14: ORD-014 RESERVED x1 (SKU-001 x1), expiresAt 11:55 <= now 12:00 -> ReservationExpired due
        // C-01: ORD-001 RESERVED, expiresAt 12:08 -> before its deadline, untouched
        // C-11: ORD-011 already RELEASED (RESERVATION_EXPIRED) -> untouched
        val c01 = stockReservationRepository.findAll().single { it.orderId == "ORD-001" }.copy()
        val c11 = stockReservationRepository.findAll().single { it.orderId == "ORD-011" }.copy()

        reservationExpiredJob().releaseExpiredReservations()

        val c14 = stockReservationRepository.findAll().single { it.orderId == "ORD-014" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, c14.status)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, c14.releaseReason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, c14.releasedAt)
        // reserved -> available
        val sku1 = position("SKU-001")
        org.junit.jupiter.api.Assertions.assertEquals(90, sku1.availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(5, sku1.reservedQuantity!!.toInt())
        // exactly one event, for ORD-014
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-014", events[0].orderId)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, events[0].reason)
        org.junit.jupiter.api.Assertions.assertEquals("RES-014", events[0].items!!.single().reservationId)
        // before the deadline: untouched
        val c01After = stockReservationRepository.findAll().single { it.orderId == "ORD-001" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RESERVED, c01After.status)
        org.junit.jupiter.api.Assertions.assertNull(c01After.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(c01.expiresAt, c01After.expiresAt)
        // already released: untouched
        val c11After = stockReservationRepository.findAll().single { it.orderId == "ORD-011" }
        org.junit.jupiter.api.Assertions.assertEquals(c11.releasedAt, c11After.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(c11.releaseReason, c11After.releaseReason)
    }

    @Test
    fun reservationExpiredJobTwiceTest() {
        reservationExpiredJob().releaseExpiredReservations()
        reservationExpiredJob().releaseExpiredReservations()

        org.junit.jupiter.api.Assertions.assertEquals(1, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(90, position("SKU-001").availableQuantity!!.toInt())
    }

    @Test
    fun reservationExpiredJobOncePerOrderTest() {
        // at 12:07 the deadlines of ORD-014 (11:55), ORD-013 (12:06) and ORD-002 (12:07, two lines) are due
        val at = Clock.fixed(Instant.parse("2026-10-01T12:07:00Z"), ZoneOffset.UTC)
        reservationExpiredJob(at).releaseExpiredReservations()

        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-002", "ORD-013", "ORD-014"), events.map { it.orderId!! }.sorted())
        org.junit.jupiter.api.Assertions.assertTrue(events.all { it.reason == StockReleaseReason.RESERVATION_EXPIRED })
        org.junit.jupiter.api.Assertions.assertEquals(
            setOf("RES-002", "RES-003"),
            events.single { it.orderId == "ORD-002" }.items!!.map { it.reservationId }.toSet(),
        )
        // ORD-001 (12:08), ORD-012 (12:09), ORD-015 (12:08) still before their deadline
        listOf("ORD-001", "ORD-012", "ORD-015").forEach { orderId ->
            org.junit.jupiter.api.Assertions.assertEquals(
                ReservationStatus.RESERVED,
                stockReservationRepository.findAll().single { it.orderId == orderId }.status,
            )
        }
    }
}
