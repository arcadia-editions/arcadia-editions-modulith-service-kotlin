package com.arcadiaeditions.fulfillment.shipping.service.impl

import com.arcadiaeditions.fulfillment.shipping.config.*
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.FulfillmentTransitions.InvalidStateTransitionException
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.events.InMemoryEventPublisher
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.inmemory.*
import com.arcadiaeditions.fulfillment.shipping.service.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
import com.arcadiaeditions.fulfillment.shipping.service.impl.mappers.*
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleOutcome
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for FulfillmentShippingService. */
class FulfillmentShippingServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val fulfillmentShippingService: FulfillmentShippingService = context.fulfillmentShippingService()

    val fulfillmentRepository: FulfillmentRepositoryInMemory = context.fulfillmentRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    val stubFulfillmentCarrier: com.arcadiaeditions.fulfillment.shipping.adapters.stub.StubFulfillmentCarrier =
        context.fulfillmentCarrier() as com.arcadiaeditions.fulfillment.shipping.adapters.stub.StubFulfillmentCarrier

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
        // StubFulfillmentCarrier is shared across tests (it's a `protected val` on the config, not
        // recreated by reloadTestData) - reset any `forcedOutcome` a previous test may have set.
        stubFulfillmentCarrier.forcedOutcome = null
    }

    private fun newInput(orderId: String, postalCode: String = "28014") =
        ScheduleFulfillmentInput(
            orderId = orderId,
            customerId = "CUST-009",
            shippingAddress =
                ShippingAddressInput(
                    name = "New Customer",
                    line1 = "1 New Street",
                    line2 = null,
                    city = "Madrid",
                    region = "Madrid",
                    postalCode = postalCode,
                    country = "ES",
                ),
            items = mutableListOf(FulfillmentItemInput(sku = "AE-EDN-001", quantity = 1 as Integer)),
        )

    // --- scheduleFulfillment ------------------------------------------------------------------

    @Test
    fun `scheduleFulfillment schedules a new fulfillment and emits one FulfillmentScheduled`() {
        val result = fulfillmentShippingService.scheduleFulfillment(newInput(orderId = "ORD-NEW-1"))

        assertEquals(FulfillmentStatus.SCHEDULED, result.status)
        assertNotNull(result.fulfillmentId)
        assertNotNull(result.scheduledAt)
        assertNotNull(result.carrierCode)
        assertNotNull(result.trackingNumber)
        assertEquals("CUST-009", result.customerId)
        assertEquals("28014", result.shippingAddress?.postalCode)
        assertEquals(1, result.items.size)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as FulfillmentScheduled
        assertEquals("ORD-NEW-1", event.orderId)
        assertEquals(result.fulfillmentId, event.fulfillmentId)
        assertEquals("CUST-009", event.customerId)
        assertNotNull(event.scheduledAt)
    }

    @Test
    fun `scheduleFulfillment fails when the carrier rejects the postal code and emits one FulfillmentFailed`() {
        val result = fulfillmentShippingService.scheduleFulfillment(newInput(orderId = "ORD-NEW-2", postalCode = "FAIL00"))

        assertEquals(FulfillmentStatus.FAILED, result.status)
        assertNotNull(result.failedAt)
        assertNotNull(result.failureReason)
        assertNull(result.scheduledAt)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as FulfillmentFailed
        assertEquals("ORD-NEW-2", event.orderId)
        assertEquals(result.failureReason, event.reason)
        assertNotNull(event.failedAt)
    }

    @Test
    fun `scheduleFulfillment fails via forcedOutcome regardless of postal code`() {
        stubFulfillmentCarrier.forcedOutcome = ScheduleOutcome.FAILED

        val result = fulfillmentShippingService.scheduleFulfillment(newInput(orderId = "ORD-NEW-3"))

        assertEquals(FulfillmentStatus.FAILED, result.status)
        assertEquals(1, eventPublisher.getEvents().size)
        assertTrue(eventPublisher.getEvents().single() is FulfillmentFailed)
    }

    @Test
    fun `scheduleFulfillment is idempotent when a fulfillment already exists for the order`() {
        // FUL-002/ORD-002 already exists (fixture).
        val result = fulfillmentShippingService.scheduleFulfillment(newInput(orderId = "ORD-002"))

        assertEquals("FUL-002", result.fulfillmentId)
        assertEquals(FulfillmentStatus.SCHEDULED, result.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- markReadyToShip ------------------------------------------------------------------------

    @Test
    fun `markReadyToShip moves a SCHEDULED fulfillment to READY_TO_SHIP and emits one FulfillmentReadyToShip`() {
        // id=1 / FUL-002 is SCHEDULED (fixture).
        val result = fulfillmentShippingService.markReadyToShip(1L)

        assertEquals(FulfillmentStatus.READY_TO_SHIP, result.status)
        assertNotNull(result.readyAt)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as FulfillmentReadyToShip
        assertEquals("FUL-002", event.fulfillmentId)
        assertEquals("ORD-002", event.orderId)
    }

    @Test
    fun `markReadyToShip on an already READY_TO_SHIP fulfillment throws InvalidStateTransitionException`() {
        // id=51 / FUL-003 is already READY_TO_SHIP (fixture).
        assertThrows<InvalidStateTransitionException> { fulfillmentShippingService.markReadyToShip(51L) }
    }

    @Test
    fun `markReadyToShip on an unknown id throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> { fulfillmentShippingService.markReadyToShip(999999L) }
    }
}
