package com.arcadiaeditions.payments.processing.application.service

import com.arcadiaeditions.payments.processing.adapter.driven.jpa.inmemory.*
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.*
import com.arcadiaeditions.payments.processing.application.port.driving.*
import com.arcadiaeditions.payments.processing.application.port.driving.dto.*
import com.arcadiaeditions.payments.processing.application.service.mapper.*
import com.arcadiaeditions.payments.processing.config.*
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.event.*
import java.time.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for PaymentsProcessingService. */
class PaymentsProcessingServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val paymentsProcessingService: PaymentsProcessingService = context.paymentsProcessingService()

    val paymentRepository: PaymentRepositoryInMemory = context.paymentRepository()

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

    private fun authorizeInput(orderId: String, paymentMethodId: String) =
        AuthorizePaymentInput(
            orderId = orderId,
            customerId = "CUST-NEW",
            customerEmail = "customer.new@example.com",
            amount = java.math.BigDecimal("49.99"),
            currency = "EUR",
            paymentMethodId = paymentMethodId,
        )

    private fun savedPayment(orderId: String) = paymentRepository.findAll().single { it.orderId == orderId }

    @Test
    fun authorizePaymentTest() {
        val before = paymentRepository.findAll().size

        val payment = paymentsProcessingService.authorizePayment(authorizeInput("ORD-NEW", "pm_card_visa"))

        org.junit.jupiter.api.Assertions.assertEquals(before + 1, paymentRepository.findAll().size)
        val saved = savedPayment("ORD-NEW")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals("CUST-NEW", saved.customerId)
        org.junit.jupiter.api.Assertions.assertEquals("customer.new@example.com", saved.customerEmail)
        org.junit.jupiter.api.Assertions.assertEquals("pm_card_visa", saved.paymentMethodId)
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal("49.99"), saved.amount)
        org.junit.jupiter.api.Assertions.assertEquals("EUR", saved.currency)
        org.junit.jupiter.api.Assertions.assertNotNull(saved.providerReference)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.authorizedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.declinedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failureReason)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentAuthorized
        org.junit.jupiter.api.Assertions.assertEquals("ORD-NEW", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-NEW", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal("49.99"), event.amount)
        org.junit.jupiter.api.Assertions.assertEquals("EUR", event.currency)
        org.junit.jupiter.api.Assertions.assertEquals(saved.providerReference, event.providerReference)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.authorizedAt)
    }

    @Test
    fun authorizePaymentDeclinedTest() {
        paymentsProcessingService.authorizePayment(authorizeInput("ORD-NEW", "pm_card_chargeDeclined"))

        val saved = savedPayment("ORD-NEW")
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.DECLINED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.declinedAt)
        org.junit.jupiter.api.Assertions.assertEquals("card_declined", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.providerReference)
        org.junit.jupiter.api.Assertions.assertNull(saved.authorizedAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentDeclined
        org.junit.jupiter.api.Assertions.assertEquals("ORD-NEW", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-NEW", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(java.math.BigDecimal("49.99"), event.amount)
        org.junit.jupiter.api.Assertions.assertEquals("EUR", event.currency)
        org.junit.jupiter.api.Assertions.assertEquals("card_declined", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.declinedAt)
    }

    @Test
    fun authorizePaymentFailedTest() {
        paymentsProcessingService.authorizePayment(authorizeInput("ORD-NEW", "pm_card_processorError"))

        val saved = savedPayment("ORD-NEW")
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.FAILED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.failedAt)
        org.junit.jupiter.api.Assertions.assertEquals("processor_timeout", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.providerReference)
        org.junit.jupiter.api.Assertions.assertNull(saved.authorizedAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentFailed
        org.junit.jupiter.api.Assertions.assertEquals("ORD-NEW", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-NEW", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(1, event.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals("processor_timeout", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.failedAt)
    }

    @Test
    fun authorizePaymentRedeliveryTest() {
        // C-01: ORD-001 already has a Payment AUTHORIZATION_REQUESTED (attempt 1)
        val before = paymentRepository.findAll().size

        val payment =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-001",
                    customerId = "CUST-001",
                    customerEmail = "customer01@example.com",
                    amount = java.math.BigDecimal("49.99"),
                    currency = "EUR",
                    paymentMethodId = "pm_card_visa",
                )
            )

        org.junit.jupiter.api.Assertions.assertEquals(before, paymentRepository.findAll().size)
        val saved = savedPayment("ORD-001")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZATION_REQUESTED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertNull(saved.providerReference)
        org.junit.jupiter.api.Assertions.assertNull(saved.authorizedAt)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun retryPaymentTest() { // TODO: implement this test
    }

    @Test
    fun capturePaymentTest() { // TODO: implement this test
    }

    @Test
    fun voidPaymentTest() { // TODO: implement this test
    }
}
