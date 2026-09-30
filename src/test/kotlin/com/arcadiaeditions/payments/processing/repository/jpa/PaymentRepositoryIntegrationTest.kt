package com.arcadiaeditions.payments.processing.repository.jpa

import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import com.arcadiaeditions.payments.processing.domain.*
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class PaymentRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var paymentRepository: PaymentRepository

    @Test
    fun findAllTest() {
        val results = paymentRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val payment = paymentRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(payment.id)
        Assertions.assertNotNull(payment.version)
    }

    @Test
    fun saveTest() {
        val payment = Payment()
        payment.paymentId = ""
        payment.orderId = ""
        payment.customerId = ""
        payment.customerEmail = ""
        payment.amount = BigDecimal.valueOf(0)
        payment.currency = ""
        payment.status = PaymentStatus.values()[0]
        payment.providerReference = ""
        payment.attemptCount = 0 as Integer
        payment.authorizedAt = Instant.now()
        payment.declinedAt = Instant.now()
        payment.failedAt = Instant.now()
        payment.capturedAt = Instant.now()
        payment.voidedAt = Instant.now()
        payment.failureReason = ""

        // Persist aggregate root
        val created = paymentRepository.save(payment)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val payment = paymentRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        payment.paymentId = ""
        payment.orderId = ""
        payment.customerId = ""
        payment.customerEmail = ""
        payment.amount = BigDecimal.valueOf(0)
        payment.currency = ""
        payment.status = PaymentStatus.values()[0]
        payment.providerReference = ""
        payment.attemptCount = 0 as Integer
        payment.authorizedAt = Instant.now()
        payment.declinedAt = Instant.now()
        payment.failedAt = Instant.now()
        payment.capturedAt = Instant.now()
        payment.voidedAt = Instant.now()
        payment.failureReason = ""

        val updated = paymentRepository.save(payment)
        Assertions.assertEquals("", updated.paymentId)
        Assertions.assertEquals("", updated.orderId)
        Assertions.assertEquals("", updated.customerId)
        Assertions.assertEquals("", updated.customerEmail)
        Assertions.assertEquals(BigDecimal.valueOf(0), updated.amount)
        Assertions.assertEquals("", updated.currency)
        Assertions.assertEquals(PaymentStatus.values()[0], updated.status)
        Assertions.assertEquals("", updated.providerReference)
        Assertions.assertEquals(0 as Integer, updated.attemptCount)
        // Phase 6 / baseline fix: see StockReservationRepositoryIntegrationTest - comparing against
        // a second independent Instant.now() call is flaky by construction.
        Assertions.assertNotNull(updated.authorizedAt)
        Assertions.assertNotNull(updated.declinedAt)
        Assertions.assertNotNull(updated.failedAt)
        Assertions.assertNotNull(updated.capturedAt)
        Assertions.assertNotNull(updated.voidedAt)
        Assertions.assertEquals("", updated.failureReason)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        paymentRepository.deleteById(id)
        val notFound = paymentRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
