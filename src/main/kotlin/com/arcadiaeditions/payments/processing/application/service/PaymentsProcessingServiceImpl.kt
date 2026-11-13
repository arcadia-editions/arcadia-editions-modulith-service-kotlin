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
) : PaymentsProcessingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val paymentsProcessingServiceMapper: PaymentsProcessingServiceMapper =
        PaymentsProcessingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    companion object {
        /** A-03: payment retry limit; at this attemptCount retryPayment emits PaymentRetryExhausted. */
        const val MAX_PAYMENT_ATTEMPTS = 3
    }

    override fun authorizePayment(input: AuthorizePaymentInput): Payment {

        log.debug("Request authorizePayment: {}", input)

        // P-01 D: PaymentRetried reuses the FAILED Payment of the order (its fields win over the input's);
        // any other existing state is a redelivery and does nothing
        val existing = paymentRepository.findByOrderId(input.orderId)
        if (existing != null && existing.status != PaymentStatus.FAILED) {
            return existing
        }
        val requested =
            if (existing != null) {
                // attemptCount was already counted by retryPayment
                existing.status = PaymentStatus.AUTHORIZATION_REQUESTED
                paymentRepository.save(existing)
            } else {
                // no Payment: the input must be complete (PaymentRetried maps placeholders), never create from them
                require(
                    input.customerEmail.isNotBlank() &&
                        input.currency.isNotBlank() &&
                        input.paymentMethodId.isNotBlank() &&
                        input.amount.signum() > 0
                ) {
                    "authorizePayment for order ${input.orderId}: no Payment exists and the input lacks " +
                        "customerEmail, amount, currency or paymentMethodId"
                }
                val payment = paymentsProcessingServiceMapper.update(Payment(), input)
                payment.status = PaymentStatus.AUTHORIZATION_REQUESTED
                payment.attemptCount = 1 as Integer
                paymentRepository.save(payment)
            }

        val result =
            paymentProcessor.authorize(
                AuthorizationRequest(
                    orderId = requested.orderId!!,
                    customerId = requested.customerId!!,
                    paymentMethodId = requested.paymentMethodId!!,
                    amount = requested.amount!!,
                    currency = requested.currency!!,
                    attemptCount = requested.attemptCount?.toInt() ?: 1,
                )
            )
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
        // redelivery of FulfillmentScheduled: already captured, nothing to do
        if (payment.status == PaymentStatus.CAPTURED) return payment
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
                // A-01 (P-02): CAPTURED is set here, as the outcome of PaymentCaptured
                payment.status = PaymentStatus.CAPTURED
                payment.capturedAt = Instant.now(clock)
                val saved = paymentRepository.save(payment)
                eventPublisher.onPaymentCaptured(eventsMapper.asPaymentCaptured(saved))
                return saved
            }
            // the processor refused the capture: CAPTURE_FAILED, only PaymentCaptureFailed (the voidPayment listener follows)
            PaymentProcessorOutcome.DECLINED,
            PaymentProcessorOutcome.FAILED -> {
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
        // A-01 (P-02): VOIDED is set here, as the outcome of PaymentVoided
        payment.status = PaymentStatus.VOIDED
        payment.voidedAt = Instant.now(clock)
        payment.failureReason = input.reason
        val saved = paymentRepository.save(payment)
        eventPublisher.onPaymentVoided(eventsMapper.asPaymentVoided(saved))
        return saved
    }
}
