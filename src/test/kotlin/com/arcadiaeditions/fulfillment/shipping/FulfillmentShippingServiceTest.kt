package com.arcadiaeditions.fulfillment.shipping

import com.arcadiaeditions.fulfillment.shipping.*
import com.arcadiaeditions.fulfillment.shipping.config.*
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.dtos.*
import com.arcadiaeditions.fulfillment.shipping.inmemory.*
import com.arcadiaeditions.fulfillment.shipping.mappers.*
import java.time.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for FulfillmentShippingService. */
class FulfillmentShippingServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val fulfillmentShippingService: FulfillmentShippingService = context.fulfillmentShippingService()

    val fulfillmentRepository: FulfillmentRepositoryInMemory = context.fulfillmentRepository()

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

    private val events get() = context.eventPublisher().getEvents()

    private fun scheduleInput(orderId: String) =
        ScheduleFulfillmentInput(
            orderId = orderId,
            customerId = "CUST-003",
            shippingAddress =
                ShippingAddressInput(
                    name = "Alex Example",
                    line1 = "Calle Ficticia 1",
                    line2 = "2B",
                    city = "Madrid",
                    region = "Madrid",
                    postalCode = "28001",
                    country = "ES",
                ),
            items = mutableListOf(FulfillmentItemInput(sku = "SKU-001", quantity = 2 as Integer)),
        )

    // C-03: OrderConfirmed, no fulfillment yet
    @Test
    fun scheduleFulfillmentTest() {
        val before = fulfillmentRepository.findAll().size

        val fulfillment = fulfillmentShippingService.scheduleFulfillment(scheduleInput("ORD-003"))

        org.junit.jupiter.api.Assertions.assertEquals(before + 1, fulfillmentRepository.findAll().size)
        val saved = fulfillmentRepository.findAll().single { it.orderId == "ORD-003" }
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, fulfillment.id)
        org.junit.jupiter.api.Assertions.assertEquals("FUL-003", saved.fulfillmentId)
        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.SCHEDULED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-003", saved.customerId)
        org.junit.jupiter.api.Assertions.assertEquals("Madrid", saved.shippingAddress!!.city)
        org.junit.jupiter.api.Assertions.assertEquals("28001", saved.shippingAddress!!.postalCode)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.items.size)
        org.junit.jupiter.api.Assertions.assertEquals("SKU-001", saved.items[0].sku)
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.items[0].quantity!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.scheduledAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failureReason)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled
        org.junit.jupiter.api.Assertions.assertEquals("FUL-003", event.fulfillmentId)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-003", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-003", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.scheduledAt)
    }

    // C-04: redelivery of OrderConfirmed, the fulfillment already exists
    @Test
    fun scheduleFulfillmentRedeliveryTest() {
        val before = fulfillmentRepository.findAll().size
        val existing = fulfillmentRepository.findAll().single { it.orderId == "ORD-004" }

        val fulfillment = fulfillmentShippingService.scheduleFulfillment(scheduleInput("ORD-004"))

        org.junit.jupiter.api.Assertions.assertEquals(before, fulfillmentRepository.findAll().size)
        org.junit.jupiter.api.Assertions.assertEquals(existing.id, fulfillment.id)
        org.junit.jupiter.api.Assertions.assertEquals("FUL-004", fulfillment.fulfillmentId)
        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.SCHEDULED, fulfillment.status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    // C-03 with the warehouse refusing: Fulfillment FAILED, FulfillmentFailed only
    @Test
    fun scheduleFulfillmentWarehouseRefusesTest() {
        context.warehouse.refusalReason = "warehouse_damage"
        val before = fulfillmentRepository.findAll().size

        val fulfillment = fulfillmentShippingService.scheduleFulfillment(scheduleInput("ORD-003"))

        org.junit.jupiter.api.Assertions.assertEquals(before + 1, fulfillmentRepository.findAll().size)
        val saved = fulfillmentRepository.findAll().single { it.orderId == "ORD-003" }
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, fulfillment.id)
        org.junit.jupiter.api.Assertions.assertEquals("FUL-003", saved.fulfillmentId)
        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.FAILED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.failedAt)
        org.junit.jupiter.api.Assertions.assertEquals("warehouse_damage", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.scheduledAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed
        org.junit.jupiter.api.Assertions.assertEquals("FUL-003", event.fulfillmentId)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-003", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-003", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals("warehouse_damage", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.failedAt)
    }

    // C-05: Fulfillment SCHEDULED, paymentCapturedAt null
    @Test
    fun recordPaymentCapturedTest() {
        val capturedAt = java.time.Instant.parse("2026-10-01T11:13:05Z")

        val fulfillment = fulfillmentShippingService.recordPaymentCaptured(RecordPaymentCapturedInput("ORD-005", capturedAt))

        val saved = fulfillmentRepository.findAll().single { it.orderId == "ORD-005" }
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, fulfillment!!.id)
        org.junit.jupiter.api.Assertions.assertEquals(capturedAt, saved.paymentCapturedAt)
        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.SCHEDULED, saved.status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    // C-24, C-06: paymentCapturedAt already set (redelivery): unchanged
    @Test
    fun recordPaymentCapturedRedeliveryTest() {
        for (orderId in listOf("ORD-024", "ORD-006")) {
            val before = fulfillmentRepository.findAll().single { it.orderId == orderId }.paymentCapturedAt
            org.junit.jupiter.api.Assertions.assertNotNull(before)

            fulfillmentShippingService.recordPaymentCaptured(
                RecordPaymentCapturedInput(orderId, java.time.Instant.parse("2026-10-01T11:59:00Z"))
            )

            org.junit.jupiter.api.Assertions.assertEquals(
                before,
                fulfillmentRepository.findAll().single { it.orderId == orderId }.paymentCapturedAt,
            )
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    // no Fulfillment for the order: no-op
    @Test
    fun recordPaymentCapturedNoFulfillmentTest() {
        val before = fulfillmentRepository.findAll().size

        val fulfillment =
            fulfillmentShippingService.recordPaymentCaptured(
                RecordPaymentCapturedInput("ORD-999", java.time.Instant.parse("2026-10-01T11:59:00Z"))
            )

        org.junit.jupiter.api.Assertions.assertNull(fulfillment)
        org.junit.jupiter.api.Assertions.assertEquals(before, fulfillmentRepository.findAll().size)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    // not SCHEDULED (C-10 FAILED): no-op
    @Test
    fun recordPaymentCapturedNotScheduledTest() {
        fulfillmentShippingService.recordPaymentCaptured(
            RecordPaymentCapturedInput("ORD-010", java.time.Instant.parse("2026-10-01T11:59:00Z"))
        )

        org.junit.jupiter.api.Assertions.assertNull(
            fulfillmentRepository.findAll().single { it.orderId == "ORD-010" }.paymentCapturedAt
        )
    }

    @Test
    fun markReadyToShipTest() {
        // C-24: SCHEDULED, payment captured
        val id = fulfillmentRepository.findAll().single { it.orderId == "ORD-024" }.id!!
        val eventsBefore = events.size

        val fulfillment = fulfillmentShippingService.markReadyToShip(id)

        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.READY_TO_SHIP, fulfillment.status)
        org.junit.jupiter.api.Assertions.assertEquals(eventsBefore + 1, events.size)
    }

    @Test
    fun markReadyToShipRefusedWithoutPaymentCapturedTest() {
        // C-04: SCHEDULED, paymentCapturedAt null
        val id = fulfillmentRepository.findAll().single { it.orderId == "ORD-004" }.id!!
        val eventsBefore = events.size

        val refusal = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            fulfillmentShippingService.markReadyToShip(id)
        }
        // the cause is named: payment not captured yet, not a state transition refusal
        org.junit.jupiter.api.Assertions.assertFalse(refusal is FulfillmentTransitions.InvalidStateTransitionException)
        org.junit.jupiter.api.Assertions.assertTrue(refusal.message!!.contains("payment not captured yet"))
        org.junit.jupiter.api.Assertions.assertTrue(refusal.message!!.contains("paymentCapturedAt"))

        org.junit.jupiter.api.Assertions.assertEquals(
            FulfillmentStatus.SCHEDULED,
            fulfillmentRepository.findById(id).get().status,
        )
        org.junit.jupiter.api.Assertions.assertEquals(eventsBefore, events.size)
    }

    // C-25: SCHEDULED, OrderCancelled -> CANCELLED, FulfillmentCancelled
    @Test
    fun cancelFulfillmentTest() {
        val fulfillment = fulfillmentShippingService.cancelFulfillment(CancelFulfillmentInput("ORD-025", "PAYMENT_VOIDED"))

        val saved = fulfillmentRepository.findAll().single { it.orderId == "ORD-025" }
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, fulfillment!!.id)
        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.CANCELLED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.cancelledAt)
        org.junit.jupiter.api.Assertions.assertEquals("PAYMENT_VOIDED", saved.cancellationReason)
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentCancelled
        org.junit.jupiter.api.Assertions.assertEquals("FUL-025", event.fulfillmentId)
        org.junit.jupiter.api.Assertions.assertEquals("ORD-025", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-025", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals("PAYMENT_VOIDED", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.cancelledAt)
    }

    // READY_TO_SHIP (C-06) is cancellable too
    @Test
    fun cancelFulfillmentFromReadyToShipTest() {
        fulfillmentShippingService.cancelFulfillment(CancelFulfillmentInput("ORD-006", "PAYMENT_VOID_FAILED"))

        val saved = fulfillmentRepository.findAll().single { it.orderId == "ORD-006" }
        org.junit.jupiter.api.Assertions.assertEquals(FulfillmentStatus.CANCELLED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
    }

    // C-21 no Fulfillment, C-26 CANCELLED (redelivery), C-10 FAILED: no-op, no event
    @Test
    fun cancelFulfillmentNoOpTest() {
        for (orderId in listOf("ORD-021", "ORD-026", "ORD-010")) {
            val before = fulfillmentRepository.findAll().map { it.id to it.status to it.cancelledAt }

            fulfillmentShippingService.cancelFulfillment(CancelFulfillmentInput(orderId, "OTHER"))

            org.junit.jupiter.api.Assertions.assertEquals(
                before,
                fulfillmentRepository.findAll().map { it.id to it.status to it.cancelledAt },
            )
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
        org.junit.jupiter.api.Assertions.assertEquals(
            "PAYMENT_VOIDED",
            fulfillmentRepository.findAll().single { it.orderId == "ORD-026" }.cancellationReason,
        )
    }
}
