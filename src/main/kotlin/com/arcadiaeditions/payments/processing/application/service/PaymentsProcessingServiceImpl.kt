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
    /** Waits the backoff before a reauthorization (ZFL header: 5 s before attempt 2, 15 s before attempt 3); tests record it instead. */
    private val backoffSleeper: BackoffSleeper = BackoffSleeper.THREAD_SLEEP,
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
        /** ZFL header: backoff before attempt 2 and before attempt 3. */
        val RETRY_BACKOFF: List<Duration> = listOf(Duration.ofSeconds(5), Duration.ofSeconds(15))

        /** Backoff before [attempt] (2, 3, ...); the last one for any later attempt. */
        fun backoffBefore(attempt: Int): Duration = RETRY_BACKOFF[(attempt - 2).coerceIn(0, RETRY_BACKOFF.size - 1)]
    }

    /** Stops the processor-call threads with the application. */
    @jakarta.annotation.PreDestroy
    fun shutdownProcessorCalls() {
        processorCalls.shutdownNow()
    }

    /**
     * One processor call, abandoned after [processorTimeout]: it then answers FAILED (processor_timeout);
     * a call that throws answers FAILED (processor_error).
     */
    private fun callProcessor(call: () -> PaymentProcessorResult): PaymentProcessorResult {
        val future = processorCalls.submit<PaymentProcessorResult> { call() }
        try {
            return future.get(processorTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            future.cancel(true)
            log.warn("payment processor did not answer within {}", processorTimeout)
            return PaymentProcessorResult.failed(PaymentProcessorResult.PROCESSOR_TIMEOUT)
        } catch (e: java.util.concurrent.ExecutionException) {
            // a processor that throws is a technical failure too (P-10 A): FAILED + PaymentFailed, not a rollback
            log.warn("payment processor call failed", e.cause ?: e)
            return PaymentProcessorResult.failed(PaymentProcessorResult.PROCESSOR_ERROR)
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
            // creation only: a Payment for the order in any state means a redelivery, nothing to do
            val existing = paymentRepository.findByOrderId(input.orderId)
            if (existing != null) {
                noOp = true
                return@execute existing
            }
            val payment = paymentsProcessingServiceMapper.update(Payment(), input)
            payment.status = PaymentStatus.AUTHORIZATION_REQUESTED
            payment.attemptCount = 1 as Integer
            paymentRepository.save(payment)
        }!!
        if (noOp) return requested

        val request =
            AuthorizationRequest(
                orderId = requested.orderId!!,
                customerId = requested.customerId!!,
                paymentMethodId = requested.paymentMethodId!!,
                amount = requested.amount!!,
                currency = requested.currency!!,
                attemptCount = attemptsOf(requested),
            )
        val result = callProcessor { paymentProcessor.authorize(request) }
        return saveAuthorizationOutcome(requested, result)
    }

    /** Saves the processor's answer on [requested] (AUTHORIZED, DECLINED or FAILED) and emits only its event. */
    private fun saveAuthorizationOutcome(requested: Payment, result: PaymentProcessorResult): Payment {
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
        // redelivery or stale PaymentFailed (another attempt, or the Payment already moved on): no-op, no event
        val attempts = attemptsOf(payment)
        if (payment.status != PaymentStatus.FAILED || input.attemptCount.toInt() != attempts) {
            log.debug("retryPayment for order {} ignored: status {}, attemptCount {} vs {}", input.orderId, payment.status, attempts, input.attemptCount)
            return payment
        }

        val now = Instant.now(clock)
        if (attempts < MAX_PAYMENT_ATTEMPTS) {
            // retried: stays FAILED, attemptCount is never incremented here (reauthorizePayment counts it)
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

    override fun voidPayment(input: VoidPaymentInput): Payment? {

        log.debug("Request voidPayment: {}", input)

        // no Payment, or nothing to void (not AUTHORIZED or CAPTURE_FAILED: redelivery, or no hold yet): no-op, no event
        val payment = paymentRepository.findByOrderId(input.orderId)
        if (payment == null || (payment.status !in listOf(PaymentStatus.AUTHORIZED, PaymentStatus.CAPTURE_FAILED))) {
            log.debug("voidPayment for order {} ignored: status {}", input.orderId, payment?.status)
            return payment
        }

        val result =
            paymentProcessor.void(
                VoidRequest(orderId = payment.orderId!!, providerReference = payment.providerReference, reason = input.reason)
            )
        payment.failureReason = input.reason
        if (result.outcome != PaymentProcessorOutcome.APPROVED) {
            // void-failed: left to ops, only PaymentVoidFailed (releaseStock follows)
            log.warn("void refused for order {}: {} ({})", input.orderId, result.reason, result.outcome)
            payment.status = PaymentStatus.VOID_FAILED
            payment.voidFailedAt = Instant.now(clock)
            val saved = paymentRepository.save(payment)
            eventPublisher.onPaymentVoidFailed(eventsMapper.asPaymentVoidFailed(saved))
            return saved
        }
        // VOIDED is the target declared in the ZDL (voidPayment transition), as PaymentVoided is the outcome
        payment.status = PaymentStatus.VOIDED
        payment.voidedAt = Instant.now(clock)
        val saved = paymentRepository.save(payment)
        eventPublisher.onPaymentVoided(eventsMapper.asPaymentVoided(saved))
        return saved
    }

    override fun reauthorizePayment(input: ReauthorizePaymentInput): Payment {

        log.debug("Request reauthorizePayment: {}", input)

        // T-80: the caller's transaction never holds a copy of the Payment. It is read and claimed in transactions of
        // their own, so the outcome saved below merges the committed attempt, not a stale copy (optimistic lock)
        val payment =
            newTransaction.execute { paymentRepository.findByOrderId(input.orderId) }
                ?: throw NoSuchElementException("Payment not found for order: ${input.orderId}")
        if (!isAttemptToRetry(payment, input)) return payment

        // backoff before the next attempt, while the Payment is still FAILED
        val attempt = attemptsOf(payment) + 1
        backoffSleeper.sleep(backoffBefore(attempt))

        // as T-52: the attempt is counted and AUTHORIZATION_REQUESTED committed in its own transaction before the call
        var noOp = false
        val requested = newTransaction.execute {
            val current = paymentRepository.findByOrderId(input.orderId)!!
            // checked again: a redelivery may have taken this attempt during the backoff
            if (!isAttemptToRetry(current, input)) {
                noOp = true
                return@execute current
            }
            current.attemptCount = attempt as Integer
            current.status = PaymentStatus.AUTHORIZATION_REQUESTED
            paymentRepository.save(current)
        }!!
        if (noOp) return requested

        val request =
            AuthorizationRequest(
                orderId = requested.orderId!!,
                customerId = requested.customerId!!,
                paymentMethodId = requested.paymentMethodId!!,
                amount = requested.amount!!,
                currency = requested.currency!!,
                attemptCount = attempt,
            )
        val result = callProcessor { paymentProcessor.authorize(request) }
        return saveAuthorizationOutcome(requested, result)
    }

    /** reauthorizePayment runs only from FAILED for the attempt that failed; anything else is a redelivery or stale. */
    private fun isAttemptToRetry(payment: Payment, input: ReauthorizePaymentInput): Boolean {
        val attempts = attemptsOf(payment)
        if (payment.status == PaymentStatus.FAILED && input.attemptCount.toInt() == attempts) return true
        log.debug("reauthorizePayment for order {} ignored: status {}, attemptCount {} vs {}", input.orderId, payment.status, attempts, input.attemptCount)
        return false
    }

    /** attemptCount is NOT NULL in the table; a Payment not yet saved has made no attempt. */
    private fun attemptsOf(payment: Payment): Int = payment.attemptCount?.toInt() ?: 0

    /** Read only, for the reservation timer: STARTED for any existing Payment, NO_PAYMENT otherwise. */
    @Transactional(readOnly = true)
    override fun getPaymentStatus(input: GetPaymentStatusInput): GetPaymentStatusResult {
        val payment = paymentRepository.findByOrderId(input.orderId)
        return if (payment == null) {
            GetPaymentStatusResult(
                outcome = GetPaymentStatusOutcome.NO_PAYMENT,
                noPayment = NoPaymentResult(orderId = input.orderId),
                started = null,
            )
        } else {
            GetPaymentStatusResult(
                outcome = GetPaymentStatusOutcome.STARTED,
                noPayment = null,
                started = PaymentStartedResult(orderId = input.orderId, status = payment.status),
            )
        }
    }
}
