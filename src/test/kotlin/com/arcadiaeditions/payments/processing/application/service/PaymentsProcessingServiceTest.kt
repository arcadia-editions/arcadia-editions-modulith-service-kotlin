package com.arcadiaeditions.payments.processing.application.service

import com.arcadiaeditions.payments.processing.adapter.driven.jpa.inmemory.*
import com.arcadiaeditions.payments.processing.adapter.driven.processor.FakePaymentProcessor
import com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessorResult
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

    /** The fake processor, observing what was committed when the authorization call starts (T-52). */
    private inner class ObservingProcessor(private val delegate: FakePaymentProcessor) :
        com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessor by delegate {
        var statusAtCall: PaymentStatus? = null
        var attemptCountAtCall: Int? = null
        var commitsAtCall: Int? = null

        override fun authorize(
            request: com.arcadiaeditions.payments.processing.application.port.driven.processor.AuthorizationRequest
        ): PaymentProcessorResult {
            val payment = paymentRepository.findByOrderId(request.orderId)
            statusAtCall = payment?.status
            attemptCountAtCall = payment?.attemptCount?.toInt()
            commitsAtCall = context.transactionManager().commits
            return delegate.authorize(request)
        }
    }

    private fun serviceWith(
        processor: com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessor,
        timeout: Duration = Duration.ofSeconds(30),
    ): PaymentsProcessingService =
        PaymentsProcessingServiceImpl(
            paymentRepository,
            context.eventPublisher(),
            processor,
            context.clock,
            context.transactionManager(),
            timeout,
        )

    @Test
    fun authorizePaymentCommitsAuthorizationRequestedBeforeTheProcessorCallTest() {
        val processor = ObservingProcessor(context.paymentProcessor())

        serviceWith(processor).authorizePayment(authorizeInput("ORD-NEW", "pm_card_visa"))

        // a call in flight is a started payment (getPaymentStatus STARTED): saved and committed before the call
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZATION_REQUESTED, processor.statusAtCall)
        org.junit.jupiter.api.Assertions.assertEquals(1, processor.attemptCountAtCall)
        org.junit.jupiter.api.Assertions.assertEquals(1, processor.commitsAtCall)
        // in its own transaction, apart from the one of the caller (the listener's)
        org.junit.jupiter.api.Assertions.assertEquals(
            listOf(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW),
            context.transactionManager().begun,
        )
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZED, savedPayment("ORD-NEW").status)
        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        org.junit.jupiter.api.Assertions.assertTrue(events[0] is PaymentAuthorized)
    }

    @Test
    fun authorizePaymentProcessorTimeoutTest() {
        val processor = context.paymentProcessor()
        try {
            // pm_card_processorNoAnswer never answers; a short timeout stands for the 30 s of the ZFL
            serviceWith(processor, Duration.ofMillis(200))
                .authorizePayment(authorizeInput("ORD-NEW", FakePaymentProcessor.NO_ANSWER_PAYMENT_METHOD))
        } finally {
            processor.releaseStalledAuthorizations()
        }

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
    fun authorizePaymentOnPaymentRetriedTest() {
        // C-12: ORD-012 Payment FAILED; PaymentRetried carries only orderId and customerId (P-01 D),
        // the other fields come from the Payment, not from the input
        val before = paymentRepository.findAll().size

        val payment =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-012",
                    customerId = "CUST-012",
                    customerEmail = "",
                    amount = java.math.BigDecimal.ZERO,
                    currency = "",
                    paymentMethodId = "",
                )
            )

        org.junit.jupiter.api.Assertions.assertEquals(before, paymentRepository.findAll().size)
        val saved = savedPayment("ORD-012")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals("pm_card_visa", saved.paymentMethodId)
        org.junit.jupiter.api.Assertions.assertEquals("customer12@example.com", saved.customerEmail)
        org.junit.jupiter.api.Assertions.assertEquals(0, java.math.BigDecimal("49.99").compareTo(saved.amount))
        org.junit.jupiter.api.Assertions.assertEquals("EUR", saved.currency)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertNotNull(saved.providerReference)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.authorizedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.failureReason)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentAuthorized
        org.junit.jupiter.api.Assertions.assertEquals("ORD-012", event.orderId)
    }

    private fun retriedInput(orderId: String, customerId: String) =
        AuthorizePaymentInput(
            orderId = orderId,
            customerId = customerId,
            customerEmail = "",
            amount = java.math.BigDecimal.ZERO,
            currency = "",
            paymentMethodId = "",
        )

    @Test
    fun authorizePaymentOnPaymentRetriedDeclinedTest() {
        // C-12: the retried authorization is declined
        context.paymentProcessor().authorizeRefusal = PaymentProcessorResult.declined("card_declined")

        paymentsProcessingService.authorizePayment(retriedInput("ORD-012", "CUST-012"))

        val saved = savedPayment("ORD-012")
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.DECLINED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.declinedAt)
        org.junit.jupiter.api.Assertions.assertEquals("card_declined", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.authorizedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.providerReference)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentDeclined
        org.junit.jupiter.api.Assertions.assertEquals("ORD-012", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("card_declined", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.declinedAt)
    }

    @Test
    fun authorizePaymentOnPaymentRetriedFailedAgainTest() {
        // C-12: the retried authorization fails again: stays FAILED, attemptCount kept, only PaymentFailed
        context.paymentProcessor().authorizeRefusal = PaymentProcessorResult.failed("processor_timeout")

        paymentsProcessingService.authorizePayment(retriedInput("ORD-012", "CUST-012"))

        val saved = savedPayment("ORD-012")
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.FAILED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(1, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.failedAt)
        org.junit.jupiter.api.Assertions.assertEquals("processor_timeout", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.declinedAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentFailed
        org.junit.jupiter.api.Assertions.assertEquals("ORD-012", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals(1, event.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.failedAt)
    }

    @Test
    fun processorErrorThenDeclineFailsFirstAttemptThenDeclinesTest() {
        // pm_card_processorErrorThenDecline: attempt 1 fails, the retry declines
        val payment = savedPayment("ORD-012")
        payment.paymentMethodId = FakePaymentProcessor.PROCESSOR_ERROR_THEN_DECLINE_PAYMENT_METHOD
        paymentRepository.save(payment)

        paymentsProcessingService.authorizePayment(retriedInput("ORD-012", "CUST-012"))
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.FAILED, savedPayment("ORD-012").status)
        org.junit.jupiter.api.Assertions.assertTrue(events.single() is PaymentFailed)

        events.clear()
        paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-012", attemptCount = 1 as Integer))
        events.clear()
        paymentsProcessingService.authorizePayment(retriedInput("ORD-012", "CUST-012"))

        val saved = savedPayment("ORD-012")
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.DECLINED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.attemptCount!!.toInt())
        org.junit.jupiter.api.Assertions.assertTrue(events.single() is PaymentDeclined)
    }

    @Test
    fun authorizePaymentOnPaymentRetriedWithoutPaymentTest() {
        // P-01 D: no Payment for the order and the placeholder fields of PaymentRetried: logged no-op, no exception
        // (an exception would mark the listener's transaction rollback-only), nothing saved, no event
        val before = paymentRepository.findAll().size

        val result =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-NO-PAYMENT",
                    customerId = "CUST-X",
                    customerEmail = "",
                    amount = java.math.BigDecimal.ZERO,
                    currency = "",
                    paymentMethodId = "",
                )
            )

        org.junit.jupiter.api.Assertions.assertNull(result.id)
        org.junit.jupiter.api.Assertions.assertEquals(before, paymentRepository.findAll().size)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun authorizePaymentOnPaymentRetriedOtherStateTest() {
        // ORD-004 Payment is not FAILED: nothing happens
        val before = savedPayment("ORD-004")

        paymentsProcessingService.authorizePayment(authorizeInput("ORD-004", "pm_card_visa"))

        org.junit.jupiter.api.Assertions.assertEquals(before.status, savedPayment("ORD-004").status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun retryPaymentTest() {
        // C-12: ORD-012 Payment FAILED (attempt 1 of 3), PaymentFailed emitted
        val before = paymentRepository.findAll().size

        val payment = paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-012", attemptCount = 1 as Integer))

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
        val payment = paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-013", attemptCount = 3 as Integer))

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
            paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-004", attemptCount = 1 as Integer))
        }
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.AUTHORIZED, savedPayment("ORD-004").status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun retryPaymentNotFoundTest() {
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException::class.java) {
            paymentsProcessingService.retryPayment(RetryPaymentInput(orderId = "ORD-MISSING", attemptCount = 1 as Integer))
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
    fun capturePaymentRefusedTest() {
        // C-04: ORD-004 Payment AUTHORIZED (PSP-AUTH-004); the processor refuses the capture
        context.paymentProcessor().captureRefusal =
            com.arcadiaeditions.payments.processing.application.port.driven.processor.PaymentProcessorResult.failed(
                "capture_window_closed"
            )

        val payment = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-004"))

        val saved = savedPayment("ORD-004")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.CAPTURE_FAILED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.failedAt)
        org.junit.jupiter.api.Assertions.assertEquals("capture_window_closed", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertEquals("PSP-AUTH-004", saved.providerReference)
        org.junit.jupiter.api.Assertions.assertNull(saved.capturedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.voidedAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentCaptureFailed
        org.junit.jupiter.api.Assertions.assertEquals("ORD-004", event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals("CUST-004", event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals("capture_window_closed", event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.failedAt)
    }

    @Test
    fun capturePaymentRedeliveryFromCaptureFailedTest() {
        // C-17: ORD-017 Payment already CAPTURE_FAILED: redelivered FulfillmentScheduled is a no-op (no processor call, no event)
        val before = savedPayment("ORD-017")
        // would approve if the processor were called: the Payment would become CAPTURED
        val payment = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-017"))

        val saved = savedPayment("ORD-017")
        org.junit.jupiter.api.Assertions.assertEquals(before.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.CAPTURE_FAILED, payment.status)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.CAPTURE_FAILED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(before.failedAt, saved.failedAt)
        org.junit.jupiter.api.Assertions.assertEquals(before.failureReason, saved.failureReason)
        org.junit.jupiter.api.Assertions.assertNull(saved.capturedAt)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun capturePaymentRedeliveryFromVoidedTest() {
        // C-10: ORD-010 Payment already VOIDED at 10:15: redelivered FulfillmentScheduled is a no-op (no processor call, no event)
        val before = savedPayment("ORD-010")
        val payment = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-010"))

        val saved = savedPayment("ORD-010")
        org.junit.jupiter.api.Assertions.assertEquals(before.id, payment.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.VOIDED, payment.status)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.VOIDED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(before.voidedAt, saved.voidedAt)
        org.junit.jupiter.api.Assertions.assertNull(saved.capturedAt)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
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

    private fun assertVoided(orderId: String, customerId: String, reason: String, providerReference: String?) {
        val saved = savedPayment(orderId)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.VOIDED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, saved.voidedAt)
        org.junit.jupiter.api.Assertions.assertEquals(reason, saved.failureReason)
        org.junit.jupiter.api.Assertions.assertEquals(providerReference, saved.providerReference)
        org.junit.jupiter.api.Assertions.assertNull(saved.capturedAt)

        org.junit.jupiter.api.Assertions.assertEquals(1, events.size)
        val event = events[0] as PaymentVoided
        org.junit.jupiter.api.Assertions.assertEquals(orderId, event.orderId)
        org.junit.jupiter.api.Assertions.assertEquals(customerId, event.customerId)
        org.junit.jupiter.api.Assertions.assertEquals(providerReference, event.providerReference)
        org.junit.jupiter.api.Assertions.assertEquals(reason, event.reason)
        org.junit.jupiter.api.Assertions.assertEquals(com.arcadiaeditions.config.TestClock.NOW, event.voidedAt)
    }

    @Test
    fun voidPaymentTest() {
        // C-16: ORD-016 Payment AUTHORIZED (PSP-AUTH-016), FulfillmentFailed emitted
        val payment =
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-016", reason = "fulfillment_failed"))

        org.junit.jupiter.api.Assertions.assertEquals(savedPayment("ORD-016").id, payment!!.id)
        org.junit.jupiter.api.Assertions.assertEquals(
            Instant.parse("2026-10-01T11:15:30Z"),
            savedPayment("ORD-016").authorizedAt,
        )
        assertVoided("ORD-016", "CUST-016", "fulfillment_failed", "PSP-AUTH-016")
    }

    @Test
    fun voidPaymentFromAuthorizationRequestedTest() {
        // C-01: ORD-001 Payment AUTHORIZATION_REQUESTED, no hold reference yet
        paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-001", reason = "fulfillment_failed"))

        assertVoided("ORD-001", "CUST-001", "fulfillment_failed", null)
    }

    @Test
    fun voidPaymentFromFailedTest() {
        // C-12: ORD-012 Payment FAILED (processor_timeout)
        paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-012", reason = "fulfillment_failed"))

        assertVoided("ORD-012", "CUST-012", "fulfillment_failed", null)
    }

    @Test
    fun voidPaymentFromCaptureFailedTest() {
        // C-17: ORD-017 Payment CAPTURE_FAILED (PSP-AUTH-017), PaymentCaptureFailed emitted
        paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-017", reason = "capture_window_closed"))

        assertVoided("ORD-017", "CUST-017", "capture_window_closed", "PSP-AUTH-017")
    }

    @Test
    fun voidPaymentRedeliveryTest() {
        // C-10: ORD-010 Payment already VOIDED at 10:15
        val payment =
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-010", reason = "warehouse_damage"))

        val saved = savedPayment("ORD-010")
        org.junit.jupiter.api.Assertions.assertEquals(saved.id, payment!!.id)
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.VOIDED, saved.status)
        org.junit.jupiter.api.Assertions.assertEquals(Instant.parse("2026-10-01T10:15:00Z"), saved.voidedAt)
        org.junit.jupiter.api.Assertions.assertEquals("fulfillment_failed", saved.failureReason)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun voidPaymentInvalidTransitionTest() {
        // C-05: ORD-005 Payment CAPTURED
        org.junit.jupiter.api.Assertions.assertThrows(PaymentTransitions.InvalidStateTransitionException::class.java) {
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-005", reason = "fulfillment_failed"))
        }
        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.CAPTURED, savedPayment("ORD-005").status)
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }

    @Test
    fun voidPaymentNotFoundTest() {
        org.junit.jupiter.api.Assertions.assertThrows(NoSuchElementException::class.java) {
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-MISSING", reason = "fulfillment_failed"))
        }
        org.junit.jupiter.api.Assertions.assertTrue(events.isEmpty())
    }
}
