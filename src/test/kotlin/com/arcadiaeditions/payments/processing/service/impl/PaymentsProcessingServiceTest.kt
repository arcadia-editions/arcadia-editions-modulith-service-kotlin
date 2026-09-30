package com.arcadiaeditions.payments.processing.service.impl

import com.arcadiaeditions.payments.processing.config.*
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.PaymentTransitions.InvalidStateTransitionException
import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.InMemoryEventPublisher
import com.arcadiaeditions.payments.processing.repository.jpa.*
import com.arcadiaeditions.payments.processing.repository.jpa.inmemory.*
import com.arcadiaeditions.payments.processing.service.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import com.arcadiaeditions.payments.processing.service.impl.mappers.*
import com.arcadiaeditions.payments.processing.service.ports.AuthorizeOutcome
import com.arcadiaeditions.payments.processing.service.ports.CaptureOutcome
import com.arcadiaeditions.payments.processing.service.ports.VoidOutcome
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for PaymentsProcessingService. */
class PaymentsProcessingServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val paymentsProcessingService: PaymentsProcessingService = context.paymentsProcessingService()

    val paymentRepository: PaymentRepositoryInMemory = context.paymentRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    val stubPaymentProvider: com.arcadiaeditions.payments.processing.adapters.stub.StubPaymentProvider =
        context.paymentProvider() as com.arcadiaeditions.payments.processing.adapters.stub.StubPaymentProvider

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
        // StubPaymentProvider is shared across tests (it's a `protected val` on the config, not
        // recreated by reloadTestData) - reset any `forced*Outcome` a previous test may have set.
        stubPaymentProvider.forcedAuthorizeOutcome = null
        stubPaymentProvider.forcedCaptureOutcome = null
        stubPaymentProvider.forcedVoidOutcome = null
    }

    // --- authorizePayment ----------------------------------------------------------------------

    @Test
    fun `authorizePayment authorizes a new payment and emits one PaymentAuthorized`() {
        val result =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-NEW-1",
                    customerId = "CUST-009",
                    customerEmail = "new@example.com",
                    amount = java.math.BigDecimal("19.99"),
                    currency = "EUR",
                    paymentMethodId = "pm_card_visa",
                )
            )

        assertEquals(PaymentStatus.AUTHORIZED, result.status)
        assertNotNull(result.paymentId)
        assertEquals(1, result.attemptCount!!.toInt())
        assertNotNull(result.authorizedAt)
        assertNotNull(result.providerReference)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentAuthorized
        assertEquals("ORD-NEW-1", event.orderId)
        assertEquals(result.paymentId, event.paymentId)
    }

    @Test
    fun `authorizePayment declines when the stub payment method id is pm_card_declined`() {
        val result =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-NEW-2",
                    customerId = "CUST-009",
                    customerEmail = "new@example.com",
                    amount = java.math.BigDecimal("19.99"),
                    currency = "EUR",
                    paymentMethodId = "pm_card_declined",
                )
            )

        assertEquals(PaymentStatus.DECLINED, result.status)
        assertNotNull(result.declinedAt)
        assertNotNull(result.failureReason)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentDeclined
        assertEquals(result.failureReason, event.reason)
    }

    @Test
    fun `authorizePayment fails when the stub payment method id is pm_card_fail`() {
        val result =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-NEW-3",
                    customerId = "CUST-009",
                    customerEmail = "new@example.com",
                    amount = java.math.BigDecimal("19.99"),
                    currency = "EUR",
                    paymentMethodId = "pm_card_fail",
                )
            )

        assertEquals(PaymentStatus.FAILED, result.status)
        assertNotNull(result.failedAt)
        assertEquals(1, result.attemptCount!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentFailed
        assertEquals(1, event.attemptCount!!.toInt())
        assertEquals(result.failureReason, event.reason)
    }

    @Test
    fun `authorizePayment is idempotent when a payment already exists for the order`() {
        // PAY-001/ORD-001 already exists (fixture).
        val result =
            paymentsProcessingService.authorizePayment(
                AuthorizePaymentInput(
                    orderId = "ORD-001",
                    customerId = "CUST-001",
                    customerEmail = "ada@example.com",
                    amount = java.math.BigDecimal("99.99"),
                    currency = "EUR",
                    paymentMethodId = "pm_card_visa",
                )
            )

        assertEquals("PAY-001", result.paymentId)
        assertEquals(PaymentStatus.AUTHORIZATION_REQUESTED, result.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- retryPayment ----------------------------------------------------------------------------

    @Test
    fun `retryPayment bumps attemptCount, emits PaymentRetried then re-authorizes and emits PaymentAuthorized`() {
        // PAY-004/ORD-004 is FAILED at attempt 1.
        val result = paymentsProcessingService.retryPayment(RetryPaymentInput(paymentId = "PAY-004", orderId = "ORD-004"))

        assertEquals(PaymentStatus.AUTHORIZED, result.status)
        assertEquals(2, result.attemptCount!!.toInt())
        assertNotNull(result.authorizedAt)

        assertEquals(2, eventPublisher.getEvents().size)
        val retried = eventPublisher.getEvents()[0] as PaymentRetried
        assertEquals("PAY-004", retried.paymentId)
        assertEquals(2, retried.attemptCount!!.toInt())
        assertNotNull(retried.retriedAt)
        val authorized = eventPublisher.getEvents()[1] as PaymentAuthorized
        assertEquals("PAY-004", authorized.paymentId)
    }

    @Test
    fun `retryPayment re-authorization can decline or fail again via forcedAuthorizeOutcome`() {
        stubPaymentProvider.forcedAuthorizeOutcome = AuthorizeOutcome.DECLINED

        val result = paymentsProcessingService.retryPayment(RetryPaymentInput(paymentId = "PAY-004", orderId = "ORD-004"))

        assertEquals(PaymentStatus.DECLINED, result.status)
        assertEquals(2, result.attemptCount!!.toInt())

        assertEquals(2, eventPublisher.getEvents().size)
        assertTrue(eventPublisher.getEvents()[0] is PaymentRetried)
        assertTrue(eventPublisher.getEvents()[1] is PaymentDeclined)
    }

    @Test
    fun `retryPayment exhausts retries on the 3rd failed attempt and emits PaymentRetryExhausted`() {
        // PAY-007/ORD-008 is FAILED at attempt 3 (fixture).
        val result = paymentsProcessingService.retryPayment(RetryPaymentInput(paymentId = "PAY-007", orderId = "ORD-008"))

        assertEquals(PaymentStatus.RETRY_EXHAUSTED, result.status)
        assertEquals(3, result.attemptCount!!.toInt())

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentRetryExhausted
        assertEquals("PAY-007", event.paymentId)
        assertEquals(3, event.attemptCount!!.toInt())
        assertEquals("PROVIDER_TIMEOUT", event.reason)
        assertNotNull(event.exhaustedAt)
    }

    @Test
    fun `retryPayment on an unknown paymentId throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> {
            paymentsProcessingService.retryPayment(RetryPaymentInput(paymentId = "PAY-DOES-NOT-EXIST", orderId = "ORD-999"))
        }
    }

    @Test
    fun `retryPayment is an idempotent no-op once the payment is no longer FAILED`() {
        // PAY-002/ORD-002 is AUTHORIZED, not FAILED: redelivery of a stale PaymentFailed (or any
        // out-of-state call) is a no-op (Q-13), not an InvalidStateTransitionException.
        val result = paymentsProcessingService.retryPayment(RetryPaymentInput(paymentId = "PAY-002", orderId = "ORD-002"))

        assertEquals(PaymentStatus.AUTHORIZED, result.status)
        assertEquals(1, result.attemptCount!!.toInt())
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- capturePayment --------------------------------------------------------------------------

    @Test
    fun `capturePayment captures an AUTHORIZED payment and emits one PaymentCaptured`() {
        // PAY-002/ORD-002 is AUTHORIZED.
        val result = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-002"))

        assertEquals(PaymentStatus.CAPTURED, result.status)
        assertNotNull(result.capturedAt)
        assertNotNull(result.providerReference)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentCaptured
        assertEquals("PAY-002", event.paymentId)
        assertEquals("carmen@example.com", event.customerEmail)
    }

    @Test
    fun `capturePayment emits PaymentCaptureFailed and keeps the payment AUTHORIZED when the provider rejects it`() {
        stubPaymentProvider.forcedCaptureOutcome = CaptureOutcome.FAILED

        val result = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-002"))

        assertEquals(PaymentStatus.AUTHORIZED, result.status)
        assertNull(result.capturedAt)
        assertNotNull(result.failureReason)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentCaptureFailed
        assertEquals("PAY-002", event.paymentId)
        assertEquals(result.failureReason, event.reason)
    }

    @Test
    fun `capturePayment on a payment not AUTHORIZED throws InvalidStateTransitionException`() {
        // PAY-005/ORD-005 is DECLINED.
        assertThrows<InvalidStateTransitionException> {
            paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-005"))
        }
    }

    @Test
    fun `capturePayment for an unknown order throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> {
            paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-DOES-NOT-EXIST"))
        }
    }

    @Test
    fun `capturePayment is idempotent when the payment is already CAPTURED`() {
        // PAY-003/ORD-003 is already CAPTURED.
        val result = paymentsProcessingService.capturePayment(CapturePaymentInput(orderId = "ORD-003"))

        assertEquals(PaymentStatus.CAPTURED, result.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- voidPayment -----------------------------------------------------------------------------

    @Test
    fun `voidPayment voids an AUTHORIZED payment and emits one PaymentVoided with the given reason`() {
        // PAY-002/ORD-002 is AUTHORIZED.
        val result =
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-002", reason = "FULFILLMENT_FAILED"))

        assertEquals(PaymentStatus.VOIDED, result.status)
        assertNotNull(result.voidedAt)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as PaymentVoided
        assertEquals("PAY-002", event.paymentId)
        assertEquals("FULFILLMENT_FAILED", event.reason)
        assertEquals(result.providerReference, event.providerReference)
    }

    @Test
    fun `voidPayment also voids from AUTHORIZATION_REQUESTED and from FAILED`() {
        // PAY-001/ORD-001 is AUTHORIZATION_REQUESTED.
        val fromRequested =
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-001", reason = "PAYMENT_CAPTURE_FAILED"))
        assertEquals(PaymentStatus.VOIDED, fromRequested.status)

        // PAY-004/ORD-004 is FAILED.
        val fromFailed =
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-004", reason = "FULFILLMENT_FAILED"))
        assertEquals(PaymentStatus.VOIDED, fromFailed.status)

        assertEquals(2, eventPublisher.getEvents().size)
    }

    @Test
    fun `voidPayment throws when the provider rejects the void, leaving the payment unchanged`() {
        stubPaymentProvider.forcedVoidOutcome = VoidOutcome.FAILED

        assertThrows<IllegalStateException> {
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-002", reason = "FULFILLMENT_FAILED"))
        }

        assertEquals(PaymentStatus.AUTHORIZED, paymentRepository.findByOrderId("ORD-002")!!.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    @Test
    fun `voidPayment on a CAPTURED payment throws InvalidStateTransitionException`() {
        // PAY-003/ORD-003 is CAPTURED - not one of [AUTHORIZED, AUTHORIZATION_REQUESTED, FAILED].
        assertThrows<InvalidStateTransitionException> {
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-003", reason = "FULFILLMENT_FAILED"))
        }
    }

    @Test
    fun `voidPayment for an unknown order throws NoSuchElementException`() {
        assertThrows<NoSuchElementException> {
            paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-DOES-NOT-EXIST", reason = "FULFILLMENT_FAILED"))
        }
    }

    @Test
    fun `voidPayment is idempotent when the payment is already VOIDED`() {
        // PAY-006/ORD-006 is already VOIDED.
        val result = paymentsProcessingService.voidPayment(VoidPaymentInput(orderId = "ORD-006", reason = "FULFILLMENT_FAILED"))

        assertEquals(PaymentStatus.VOIDED, result.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }
}
