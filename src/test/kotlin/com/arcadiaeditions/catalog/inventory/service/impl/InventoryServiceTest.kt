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
    fun releaseStockVoidFailedFromConfirmedTest() {
        // C-27: ORD-027 CONFIRMED x1 (SKU-006 x1), PaymentVoidFailed
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-027", reason = StockReleaseReason.PAYMENT_VOID_FAILED))

        val saved = stockReservationRepository.findAll().single { it.orderId == "ORD-027" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.PAYMENT_VOID_FAILED, saved.releaseReason)
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T11:21:00Z"), saved.confirmedAt)
        // sold -> available
        val sku6 = position("SKU-006")
        org.junit.jupiter.api.Assertions.assertEquals(17, sku6.availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(1, sku6.reservedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(2, sku6.soldQuantity!!.toInt())
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.PAYMENT_VOID_FAILED, events[0].reason)
        org.junit.jupiter.api.Assertions.assertEquals("RES-027", events[0].items!!.single().reservationId)
    }

    @Test
    fun releaseStockVoidFailedRedeliveryTest() {
        // C-28: ORD-028 already RELEASED (PAYMENT_VOID_FAILED) -> no-op, no event
        inventoryService.releaseStock(ReleaseStockInput(orderId = "ORD-028", reason = StockReleaseReason.PAYMENT_VOID_FAILED))

        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
        val saved = stockReservationRepository.findAll().single { it.orderId == "ORD-028" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T09:26:10Z"), saved.releasedAt)
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

        org.junit.jupiter.api.Assertions.assertEquals(ConfirmStockReservationOutcome.CONFIRMED, result.outcome)
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
        org.junit.jupiter.api.Assertions.assertEquals(ConfirmStockReservationOutcome.CONFIRMED, result.outcome)
        val after = stockReservationRepository.findAll().single { it.orderId == "ORD-003" }
        org.junit.jupiter.api.Assertions.assertEquals(before.confirmedAt, after.confirmedAt)
        org.junit.jupiter.api.Assertions.assertEquals(5, position("SKU-001").soldQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(6, position("SKU-001").reservedQuantity!!.toInt())
    }

    @Test
    fun confirmStockReservationNotHeldTest() {
        // C-23: ORD-023 RELEASED by the timer -> NOT_HELD, no change, no event
        val before = stockReservationRepository.findAll().single { it.orderId == "ORD-023" }.copy()
        val soldBefore = position("SKU-001").soldQuantity!!.toInt()
        val reservedBefore = position("SKU-001").reservedQuantity!!.toInt()
        val result = inventoryService.confirmStockReservation(ConfirmStockReservationInput(orderId = "ORD-023"))

        org.junit.jupiter.api.Assertions.assertEquals(ConfirmStockReservationOutcome.NOT_HELD, result.outcome)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-023", result.notHeld!!.orderId)
        org.junit.jupiter.api.Assertions.assertNull(result.confirmed)
        org.junit.jupiter.api.Assertions.assertEquals(0, confirmedEvents().size)
        val after = stockReservationRepository.findAll().single { it.orderId == "ORD-023" }
        org.junit.jupiter.api.Assertions.assertEquals(before, after)
        org.junit.jupiter.api.Assertions.assertEquals(soldBefore, position("SKU-001").soldQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(reservedBefore, position("SKU-001").reservedQuantity!!.toInt())
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

    /** getPaymentStatus calls, by orderId, through the payments module's public API. */
    private val paymentStatusCalls = mutableListOf<String>()

    private fun countingPayments(): com.arcadiaeditions.payments.processing.application.port.driving.PaymentsProcessingService {
        val real: com.arcadiaeditions.payments.processing.application.port.driving.PaymentsProcessingService =
            context.paymentsContext.paymentsProcessingService()
        return object : com.arcadiaeditions.payments.processing.application.port.driving.PaymentsProcessingService by real {
            override fun getPaymentStatus(
                input: com.arcadiaeditions.payments.processing.application.port.driving.dto.GetPaymentStatusInput
            ): com.arcadiaeditions.payments.processing.application.port.driving.dto.GetPaymentStatusResult {
                paymentStatusCalls.add(input.orderId)
                return real.getPaymentStatus(input)
            }
        }
    }

    private fun countingInventoryService(clock: Clock = context.clock) =
        InventoryServiceImpl(stockReservationRepository, context.inventoryPositionRepository(), context.eventPublisher(), clock, countingPayments())

    private fun reservationExpiredJob(clock: Clock = context.clock) =
        com.arcadiaeditions.catalog.inventory.events.scheduler.ReservationExpiredJob(
            stockReservationRepository,
            countingInventoryService(clock),
            clock,
        )

    @Test
    fun expireReservationNoPaymentTest() {
        // C-30: ORD-030 RESERVED x1 (SKU-006 x1), past its deadline, no Payment -> NO_PAYMENT -> released
        val result = countingInventoryService().expireReservation(ExpireReservationInput(orderId = "ORD-030"))

        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-030"), paymentStatusCalls)
        org.junit.jupiter.api.Assertions.assertEquals(1, result.size)
        val saved = stockReservationRepository.findAll().single { it.orderId == "ORD-030" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, saved.releaseReason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.releasedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.confirmedAt)
        // reserved -> available
        val sku6 = position("SKU-006")
        org.junit.jupiter.api.Assertions.assertEquals(17, sku6.availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(0, sku6.reservedQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(3, sku6.soldQuantity!!.toInt())
        // exactly one event with the items
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-030", events[0].orderId)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, events[0].reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, events[0].releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(
            listOf("RES-030:SKU-006:1"),
            events[0].items!!.map { "${it.reservationId}:${it.sku}:${it.quantity}" },
        )
    }

    @Test
    fun expireReservationPaymentStartedTest() {
        // C-14: ORD-014 RESERVED x1, past its deadline, Payment FAILED (attempt 2) -> STARTED -> payment owns the end
        val before = stockReservationRepository.findAll().single { it.orderId == "ORD-014" }.copy()
        countingInventoryService().expireReservation(ExpireReservationInput(orderId = "ORD-014"))

        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-014"), paymentStatusCalls)
        org.junit.jupiter.api.Assertions.assertEquals(before, stockReservationRepository.findAll().single { it.orderId == "ORD-014" })
        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(89, position("SKU-001").availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(6, position("SKU-001").reservedQuantity!!.toInt())
    }

    @Test
    fun expireReservationPaymentInFlightTest() {
        // C-01: ORD-001 RESERVED, Payment AUTHORIZATION_REQUESTED (first call in flight) -> STARTED -> nothing
        countingInventoryService().expireReservation(ExpireReservationInput(orderId = "ORD-001"))

        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-001"), paymentStatusCalls)
        org.junit.jupiter.api.Assertions.assertEquals(
            ReservationStatus.RESERVED,
            stockReservationRepository.findAll().single { it.orderId == "ORD-001" }.status,
        )
        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
    }

    @Test
    fun expireReservationNothingReservedTest() {
        // C-11: ORD-011 already RELEASED (RESERVATION_EXPIRED) -> late fire: no-op, no event, payments not asked
        val before = stockReservationRepository.findAll().single { it.orderId == "ORD-011" }.copy()
        countingInventoryService().expireReservation(ExpireReservationInput(orderId = "ORD-011"))

        org.junit.jupiter.api.Assertions.assertEquals(emptyList<String>(), paymentStatusCalls)
        org.junit.jupiter.api.Assertions.assertEquals(before, stockReservationRepository.findAll().single { it.orderId == "ORD-011" })
        org.junit.jupiter.api.Assertions.assertEquals(0, releasedEvents().size)
    }

    @Test
    fun expireReservationTwiceTest() {
        countingInventoryService().expireReservation(ExpireReservationInput(orderId = "ORD-030"))
        countingInventoryService().expireReservation(ExpireReservationInput(orderId = "ORD-030"))

        org.junit.jupiter.api.Assertions.assertEquals(1, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(17, position("SKU-006").availableQuantity!!.toInt())
    }

    @Test
    fun reservationExpiredJobTest() {
        // due at 12:00: C-30 ORD-030 (11:50, no Payment) and C-14 ORD-014 (11:55, payment started)
        // C-01: ORD-001 RESERVED, expiresAt 12:08 -> before its deadline, untouched
        // C-11: ORD-011 already RELEASED (RESERVATION_EXPIRED) -> untouched
        val c01 = stockReservationRepository.findAll().single { it.orderId == "ORD-001" }.copy()
        val c11 = stockReservationRepository.findAll().single { it.orderId == "ORD-011" }.copy()
        val c14 = stockReservationRepository.findAll().single { it.orderId == "ORD-014" }.copy()

        reservationExpiredJob().releaseExpiredReservations()

        // payments asked once per due order, only for the due ones
        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-014", "ORD-030"), paymentStatusCalls.sorted())
        // C-30 released
        val c30 = stockReservationRepository.findAll().single { it.orderId == "ORD-030" }
        org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RELEASED, c30.status)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, c30.releaseReason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, c30.releasedAt)
        org.junit.jupiter.api.Assertions.assertEquals(17, position("SKU-006").availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(0, position("SKU-006").reservedQuantity!!.toInt())
        // exactly one event, for ORD-030
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-030", events[0].orderId)
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, events[0].reason)
        org.junit.jupiter.api.Assertions.assertEquals("RES-030", events[0].items!!.single().reservationId)
        // C-14 kept: payment started, payment owns the end
        org.junit.jupiter.api.Assertions.assertEquals(c14, stockReservationRepository.findAll().single { it.orderId == "ORD-014" })
        org.junit.jupiter.api.Assertions.assertEquals(89, position("SKU-001").availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(6, position("SKU-001").reservedQuantity!!.toInt())
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

        // ORD-030 released once; ORD-014 (payment started) still RESERVED and due: asked again, kept again
        org.junit.jupiter.api.Assertions.assertEquals(1, releasedEvents().size)
        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-014", "ORD-014", "ORD-030"), paymentStatusCalls.sorted())
        org.junit.jupiter.api.Assertions.assertEquals(17, position("SKU-006").availableQuantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(89, position("SKU-001").availableQuantity!!.toInt())
    }

    @Test
    fun reservationExpiredJobOncePerOrderTest() {
        // at 12:07 the deadlines of ORD-030 (11:50), ORD-014 (11:55), ORD-013 (12:06) and ORD-002 (12:07, two lines) are due
        val at = Clock.fixed(Instant.parse("2026-10-01T12:07:00Z"), ZoneOffset.UTC)
        reservationExpiredJob(at).releaseExpiredReservations()

        // one getPaymentStatus per due order (ORD-002 has two lines)
        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-002", "ORD-013", "ORD-014", "ORD-030"), paymentStatusCalls.sorted())
        // only ORD-030 has no Payment: one event, for it
        val events = releasedEvents()
        org.junit.jupiter.api.Assertions.assertEquals(listOf("ORD-030"), events.map { it.orderId!! })
        org.junit.jupiter.api.Assertions.assertEquals(StockReleaseReason.RESERVATION_EXPIRED, events[0].reason)
        // payment started (ORD-002, ORD-013, ORD-014) or before the deadline (ORD-001, ORD-012, ORD-015): still RESERVED
        listOf("ORD-002", "ORD-013", "ORD-014", "ORD-001", "ORD-012", "ORD-015").forEach { orderId ->
            stockReservationRepository.findAll().filter { it.orderId == orderId }.forEach {
                org.junit.jupiter.api.Assertions.assertEquals(ReservationStatus.RESERVED, it.status, orderId)
            }
        }
    }
}
