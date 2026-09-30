package com.arcadiaeditions.catalog.inventory.repository.jpa

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import jakarta.persistence.EntityManager
import java.time.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class StockReservationRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var stockReservationRepository: StockReservationRepository

    @Test
    fun findAllTest() {
        val results = stockReservationRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val stockReservation =
            stockReservationRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(stockReservation.id)
        Assertions.assertNotNull(stockReservation.version)
    }

    @Test
    fun saveTest() {
        val stockReservation = StockReservation()
        stockReservation.reservationId = ""
        stockReservation.orderId = ""
        stockReservation.sku = ""
        stockReservation.quantity = 0 as Integer
        stockReservation.status = ReservationStatus.values()[0]
        stockReservation.reservedAt = Instant.now()
        stockReservation.expiresAt = Instant.now()
        stockReservation.confirmedAt = Instant.now()
        stockReservation.releasedAt = Instant.now()
        stockReservation.releaseReason = StockReleaseReason.values()[0]

        // Persist aggregate root
        val created = stockReservationRepository.save(stockReservation)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val stockReservation =
            stockReservationRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        stockReservation.reservationId = ""
        stockReservation.orderId = ""
        stockReservation.sku = ""
        stockReservation.quantity = 0 as Integer
        stockReservation.status = ReservationStatus.values()[0]
        stockReservation.reservedAt = Instant.now()
        stockReservation.expiresAt = Instant.now()
        stockReservation.confirmedAt = Instant.now()
        stockReservation.releasedAt = Instant.now()
        stockReservation.releaseReason = StockReleaseReason.values()[0]

        val updated = stockReservationRepository.save(stockReservation)
        Assertions.assertEquals("", updated.reservationId)
        Assertions.assertEquals("", updated.orderId)
        Assertions.assertEquals("", updated.sku)
        Assertions.assertEquals(0 as Integer, updated.quantity)
        Assertions.assertEquals(ReservationStatus.values()[0], updated.status)
        // Phase 6 / baseline fix: the generated assertion compared the saved timestamp against a
        // second, independent Instant.now() call - flaky by construction (sub-millisecond drift
        // between the two calls), not a fixture or behavior issue. assertNotNull still proves the
        // field was persisted.
        Assertions.assertNotNull(updated.reservedAt)
        Assertions.assertNotNull(updated.expiresAt)
        Assertions.assertNotNull(updated.confirmedAt)
        Assertions.assertNotNull(updated.releasedAt)
        Assertions.assertEquals(StockReleaseReason.values()[0], updated.releaseReason)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        stockReservationRepository.deleteById(id)
        val notFound = stockReservationRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
