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
    fun retryPaymentTest() {
        // C-12: ORD-012 Payment FAILED (attempt 1 of 3), PaymentFailed emitted
        val before = paymentRepository.findAll().size

        val payment = paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-012"))

        org.junit.jupiter.api.Assertions.assertEquals(before, paymentRepository.findAll().size)
        val saved = savedPayment("ORD-012")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.FAILED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T11:59:30Z"), saved.failedAt)
        org.junit.jupiter.api.Assertions.assertEquals("processor_timeout", saved.failureReason)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentRetried
        org.junit.jupiter.api.Assertions.assertEquals("ORD-012", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-012", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(2, event.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.retriedAt)
    }

    @Test
    fun retryPaymentExhaustedTest() {
        // C-13: ORD-013 Payment FAILED at the retry limit (attempt 3, A-03)
        val payment = paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-013"))

        val saved = savedPayment("ORD-013")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.RETRY_EXHAUSTED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(3, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals("processor_timeout", saved.failureReason)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentRetryExhausted
        org.junit.jupiter.api.Assertions.assertEquals("ORD-013", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-013", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(3, event.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals("processor_timeout", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.exhaustedAt)
    }

    @Test
    fun retryPaymentInvalidTransitionTest() {
        // C-04: ORD-004 Payment AUTHORIZED
        org.junit.jupiter.api.Assertions.assertThrows(PaymentTransitions.InvalidStateTransitionException::class.java) {
            paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-004"))
        }
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZED, savedPayment("ORD-004").status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun retryPaymentNotFoundTest() {
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException::class.java) {
            paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-MISSING"))
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun capturePaymentTest() {
        // C-04: ORD-004 Payment AUTHORIZED (PSP-AUTH-004), FulfillmentScheduled emitted
        val payment = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-004"))

        val saved = savedPayment("ORD-004")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.CAPTURED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.capturedAt)
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T11:20:30Z"), saved.authorizedAt)
        org.junit.jupiter.api.Assertions.assertEquals("PSP-AUTH-004", saved.providerReference)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertNull(saved.failedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.voidedAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentCaptured
        org.junit.jupiter.api.Assertions.assertEquals("ORD-004", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-004", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals("customer04@example.com", event.customerEmail)
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal("49.99").compareTo(event.amount))
        org.junit.jupiter.api.Assertions.assertEquals("EUR", event.currency)
        org.junit.jupiter.api.Assertions.assertEquals("PSP-AUTH-004", event.providerReference)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.capturedAt)
    }

    @Test
    fun capturePaymentRedeliveryTest() {
        // C-05: ORD-005 Payment already CAPTURED at 11:13
        val payment = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-005"))

        val saved = savedPayment("ORD-005")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.CAPTURED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T11:13:00Z"), saved.capturedAt)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun capturePaymentInvalidTransitionTest() {
        // C-01: ORD-001 Payment AUTHORIZATION_REQUESTED
        org.junit.jupiter.api.Assertions.assertThrows(PaymentTransitions.InvalidStateTransitionException::class.java) {
            paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-001"))
        }
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZATION_REQUESTED, savedPayment("ORD-001").status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun capturePaymentNotFoundTest() {
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException::class.java) {
            paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-MISSING"))
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun voidPaymentTest() { // TODO: implement this test
    }
}
