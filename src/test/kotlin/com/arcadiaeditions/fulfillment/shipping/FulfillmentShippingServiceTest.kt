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
    fun markReadyToShipTest() { // TODO: implement this test
    }
}
