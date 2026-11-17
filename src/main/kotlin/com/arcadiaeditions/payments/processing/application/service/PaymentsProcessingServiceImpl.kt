package com.arcadiaeditions.payments.processing.application.service

import com.arcadiaeditions.payments.processing.application.port.driven.event.*
import com.arcadiaeditions.payments.processing.application.port.driven.jpa.*
import com.arcadiaeditions.payments.processing.application.port.driven.processor.*
import com.arcadiaeditions.payments.processing.application.port.driving.*
import com.arcadiaeditions.payments.processing.application.port.driving.dto.*
import com.arcadiaeditions.payments.processing.application.service.mapper.*
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.event.*
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Payment]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.Application
open class PaymentsProcessingServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: EventPublisher,
    private val paymentProcessor: PaymentProcessor,
    private val clock: java.time.Clock,
    transactionManager: org.springframework.transaction.PlatformTransactionManager,
    /** ZFL header: one processor call lasts at most 30 seconds; then it is a technical failure (processor_timeout). */
    @org.springframework.beans.factory.annotation.Value("\${arcadia.payments.processor-timeout:30s}")
    private val processorTimeout: Duration,
) : PaymentsProcessingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val paymentsProcessingServiceMapper: PaymentsProcessingServiceMapper =
        PaymentsProcessingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    /** Commits AUTHORIZATION_REQUESTED apart from the caller's transaction, before the processor call (T-52). */
    private val newTransaction: org.springframework.transaction.support.TransactionTemplate =
        org.springframework.transaction.support.TransactionTemplate(transactionManager).apply {
            propagationBehavior = org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    /** Runs each processor call, so the caller can stop waiting after [processorTimeout]. */
    private val processorCalls: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()

    companion object {
        /** A-03: payment retry limit; at this attemptCount retryPayment emits PaymentRetryExhausted. */
        const val MAX_PAYMENT_ATTEMPTS = 3
        /** A-06: failureReason of a processor call that did not answer within the timeout. */
        const val PROCESSOR_TIMEOUT = "processor_timeout"
    }

    /** One processor call, abandoned after [processorTimeout]: it then answers FAILED (processor_timeout). */
    private fun callProcessor(call: () -> PaymentProcessorResult): PaymentProcessorResult {
        val future = processorCalls.submit<PaymentProcessorResult> { call() }
        try {
            return future.get(processorTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            log.warn("payment processor did not answer within {}", processorTimeout)
            return PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw e
        }
    }

    override fun authorizePayment(input: AuthorizePaymentInput): Payment {

        log.debug("Request authorizePayment: {}", input)

        // T-52: AUTHORIZATION_REQUESTED is committed in its own transaction before the processor call, so a call in
        // flight counts as started (getPaymentStatus); the outcome is saved afterwards, in the caller's transaction
        var noOp = false
        val requested = newTransaction.execute {
            // P-01 D: PaymentRetried reuses the FAILED Payment of the order (its fields win over the input's);
            // any other existing state is a redelivery and does nothing
            val existing = paymentRepository.findByOrderId(input.orderId)
            if (existing != null && existing.status != PaymentStatus.FAILED) {
                noOp = true
                return@execute existing
            }
            if (existing != null) {
                // attemptCount was already counted by retryPayment
                existing.status = PaymentStatus.AUTHORIZATION_REQUESTED
                paymentRepository.save(existing)
            } else {
                // no Payment: PaymentRetried maps placeholders, never create from them; a logged no-op, not an
                // exception (it would mark the listener's REQUIRES_NEW transaction rollback-only and the publication
                // would be resubmitted forever)
                if (input.customerEmail.isBlank() || input.currency.isBlank() || input.paymentMethodId.isBlank()) {
                    log.warn("authorizePayment for order {}: no Payment exists and the input is incomplete, ignored", input.orderId)
                    noOp = true
                    return@execute Payment()
                }
                val payment = paymentsProcessingServiceMapper.update(Payment(), input)
                payment.status = PaymentStatus.AUTHORIZATION_REQUESTED
                payment.attemptCount = 1 as Integer
                paymentRepository.save(payment)
            }
        }!!
        if (noOp) return requested

        val request =
            AuthorizationRequest(
                orderId = requested.orderId!!,
                customerId = requested.customerId!!,
                paymentMethodId = requested.paymentMethodId!!,
                amount = requested.amount!!,
                currency = requested.currency!!,
                attemptCount = requested.attemptCount?.toInt() ?: 1,
            )
        val result = callProcessor { paymentProcessor.authorize(request) }
        // A-01 (P-02): the outcome state is set here, though the ZDL transition only names AUTHORIZATION_REQUESTED
        val now = Instant.now(clock)
        // a retried authorization starts without the failure of the previous attempt
        requested.failedAt = null
        requested.failureReason = null
        when (result.outcome) {
            PaymentProcessorOutcome.APPROVED -> {
                requested.status = PaymentStatus.AUTHORIZED
                requested.providerReference = result.providerReference
                requested.authorizedAt = now
            }
            PaymentProcessorOutcome.DECLINED -> {
                requested.status = PaymentStatus.DECLINED
                requested.declinedAt = now
                requested.failureReason = result.reason
            }
            PaymentProcessorOutcome.FAILED -> {
                requested.status = PaymentStatus.FAILED
                requested.failedAt = now
                requested.failureReason = result.reason
            }
        }
        val saved = paymentRepository.save(requested)

        // emit only the outcome's event
        when (result.outcome) {
            PaymentProcessorOutcome.APPROVED -> eventPublisher.onPaymentAuthorized(eventsMapper.asPaymentAuthorized(saved))
            PaymentProcessorOutcome.DECLINED -> eventPublisher.onPaymentDeclined(eventsMapper.asPaymentDeclined(saved))
            PaymentProcessorOutcome.FAILED -> eventPublisher.onPaymentFailed(eventsMapper.asPaymentFailed(saved))
        }
        return saved
    }

    override fun retryPayment(input: RetryPaymentInput): Payment {

        log.debug("Request retryPayment: {}", input)

        val payment =
            paymentRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Payment not found for order: ${input.orderId}")
        PaymentTransitions.ensureCanRetryPayment(payment)

        val now = Instant.now(clock)
        val attempts = payment.attemptCount?.toInt() ?: 0
        if (attempts < MAX_PAYMENT_ATTEMPTS) {
            // retried: stays FAILED, the next attempt is counted here; authorizePayment runs it on PaymentRetried (T-23)
            payment.attemptCount = (attempts + 1) as Integer
            val saved = paymentRepository.save(payment)
            eventPublisher.onPaymentRetried(eventsMapper.asPaymentRetried(saved).copy(retriedAt = now))
            return saved
        }
        // retry-exhausted: A-03 limit reached
        payment.status = PaymentStatus.RETRY_EXHAUSTED
        val saved = paymentRepository.save(payment)
        eventPublisher.onPaymentRetryExhausted(eventsMapper.asPaymentRetryExhausted(saved).copy(exhaustedAt = now))
        return saved
    }

    override fun capturePayment(input: CapturePaymentInput): Payment {

        log.debug("Request capturePayment: {}", input)

        val payment =
            paymentRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Payment not found for order: ${input.orderId}")
        // redelivery of FulfillmentScheduled: already captured or capture already failed/voided, nothing to do
        if (
            payment.status == PaymentStatus.CAPTURED ||
                payment.status == PaymentStatus.CAPTURE_FAILED ||
                payment.status == PaymentStatus.VOIDED
        ) {
            return payment
        }
        PaymentTransitions.ensureCanCapturePayment(payment)

        val result =
            paymentProcessor.capture(
                CaptureRequest(
                    orderId = payment.orderId!!,
                    providerReference = payment.providerReference,
                    amount = payment.amount!!,
                    currency = payment.currency!!,
                )
            )
        when (result.outcome) {
            PaymentProcessorOutcome.APPROVED -> {
                // CAPTURED is the target declared in the ZDL (capturePayment transition), as PaymentCaptured is the outcome
                payment.status = PaymentStatus.CAPTURED
                payment.capturedAt = Instant.now(clock)
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentCaptured(eventsMapper.asPaymentCaptured(saved))
                return saved
            }
            // the processor refused the capture: CAPTURE_FAILED, only PaymentCaptureFailed (the voidPayment listener follows)
            PaymentProcessorOutcome.DECLINED,
            PaymentProcessorOutcome.FAILED -> {
                // A-01 (P-02): CAPTURE_FAILED is set here, as the outcome of PaymentCaptureFailed
                payment.status = PaymentStatus.CAPTURE_FAILED
                payment.failedAt = Instant.now(clock)
                payment.failureReason = result.reason
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentCaptureFailed(eventsMapper.asPaymentCaptureFailed(saved))
                return saved
            }
        }
    }

    override fun voidPayment(input: VoidPaymentInput): Payment {

        log.debug("Request voidPayment: {}", input)

        val payment =
            paymentRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Payment not found for order: ${input.orderId}")
        // redelivery of FulfillmentFailed / PaymentCaptureFailed: already voided, nothing to do
        if (payment.status == PaymentStatus.VOIDED) return payment
        PaymentTransitions.ensureCanVoidPayment(payment)

        val result =
            paymentProcessor.void(
                VoidRequest(orderId = payment.orderId!!, providerReference = payment.providerReference, reason = input.reason)
            )
        if (result.outcome != PaymentProcessorOutcome.APPROVED) {
            // the models have no void-refused outcome: fail so the event publication stays incomplete and is retried
            throw IllegalStateException("voidPayment refused by the processor for order ${input.orderId}: ${result.reason}")
        }
        // VOIDED is the target declared in the ZDL (voidPayment transition), as PaymentVoided is the outcome
        payment.status = PaymentStatus.VOIDED
        payment.voidedAt = Instant.now(clock)
        payment.failureReason = input.reason
        val saved = paymentRepository.save(payment)
        eventPublisher.onPaymentVoided(eventsMapper.asPaymentVoided(saved))
        return saved
    }

    // baseline/place-order-flow-2 compile stub: implemented by a task of docs/tasks.md (PaymentRetried still reaches authorizePayment, P-01 D, until then)
    override fun reauthorizePayment(input: ReauthorizePaymentInput): Payment {
        TODO("reauthorizePayment: " + input.orderId)
    }

    // baseline/place-order-flow-2 compile stub: implemented by a task of docs/tasks.md
    override fun getPaymentStatus(input: GetPaymentStatusInput): GetPaymentStatusResult {
        TODO("getPaymentStatus: " + input.orderId)
    }
}
