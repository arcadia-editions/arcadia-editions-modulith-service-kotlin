package com.arcadiaeditions.fulfillment.shipping.repository.jpa

import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.persistence.EntityManager
import java.time.*
import java.util.List
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class FulfillmentRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var fulfillmentRepository: FulfillmentRepository

    @Test
    fun findAllTest() {
        val results = fulfillmentRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val fulfillment =
            fulfillmentRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(fulfillment.id)
        Assertions.assertNotNull(fulfillment.version)
    }

    @Test
    fun saveTest() {
        val fulfillment = Fulfillment()
        fulfillment.fulfillmentId = ""
        fulfillment.orderId = ""
        fulfillment.customerId = ""
        fulfillment.status = FulfillmentStatus.values()[0]
        fulfillment.shippingAddress = ShippingAddress()
        fulfillment.items = List.of(FulfillmentItem())
        fulfillment.carrierCode = ""
        fulfillment.trackingNumber = ""
        fulfillment.scheduledAt = Instant.now()
        fulfillment.readyAt = Instant.now()
        fulfillment.failedAt = Instant.now()
        fulfillment.failureReason = ""

        // Persist aggregate root
        val created = fulfillmentRepository.save(fulfillment)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val fulfillment =
            fulfillmentRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        fulfillment.fulfillmentId = ""
        fulfillment.orderId = ""
        fulfillment.customerId = ""
        fulfillment.status = FulfillmentStatus.values()[0]
        fulfillment.shippingAddress = ShippingAddress()
        fulfillment.items = List.of(FulfillmentItem())
        fulfillment.carrierCode = ""
        fulfillment.trackingNumber = ""
        fulfillment.scheduledAt = Instant.now()
        fulfillment.readyAt = Instant.now()
        fulfillment.failedAt = Instant.now()
        fulfillment.failureReason = ""

        val updated = fulfillmentRepository.save(fulfillment)
        Assertions.assertEquals("", updated.fulfillmentId)
        Assertions.assertEquals("", updated.orderId)
        Assertions.assertEquals("", updated.customerId)
        Assertions.assertEquals(FulfillmentStatus.values()[0], updated.status)
        Assertions.assertEquals(ShippingAddress(), updated.shippingAddress)
        Assertions.assertEquals(List.of(FulfillmentItem()), updated.items)
        Assertions.assertEquals("", updated.carrierCode)
        Assertions.assertEquals("", updated.trackingNumber)
        // Phase 6 / baseline fix: see StockReservationRepositoryIntegrationTest - comparing against
        // a second independent Instant.now() call is flaky by construction.
        Assertions.assertNotNull(updated.scheduledAt)
        Assertions.assertNotNull(updated.readyAt)
        Assertions.assertNotNull(updated.failedAt)
        Assertions.assertEquals("", updated.failureReason)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        fulfillmentRepository.deleteById(id)
        val notFound = fulfillmentRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
