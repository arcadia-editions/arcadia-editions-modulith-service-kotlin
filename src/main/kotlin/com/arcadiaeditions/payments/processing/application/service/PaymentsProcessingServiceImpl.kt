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

    override fun authorizePayment(input: AuthorizePaymentInput): Payment {

        log.debug("Request authorizePayment: {}", input)

        // redelivery: the orderId already has a Payment (reuse of a FAILED one on PaymentRetried is T-23, P-01 D)
        paymentRepository.findByOrderId(input.orderId)?.let {
            return it
        }

        val payment = paymentsProcessingServiceMapper.update(Payment(), input)
        payment.status = PaymentStatus.AUTHORIZATION_REQUESTED
        payment.attemptCount = 1 as Integer
        val requested = paymentRepository.save(payment)

        val result =
            paymentProcessor.authorize(
                AuthorizationRequest(
                    orderId = input.orderId,
                    customerId = input.customerId,
                    paymentMethodId = input.paymentMethodId,
                    amount = input.amount,
                    currency = input.currency,
                )
            )
        // A-01 (P-02): the outcome state is set here, though the ZDL transition only names AUTHORIZATION_REQUESTED
        val now = Instant.now(clock)
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

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        val foundPayment: Payment? = emptyList<Payment>().firstOrNull()
        return foundPayment
            ?.let { existingPayment ->
                PaymentTransitions.ensureCanRetryPayment(existingPayment)
                paymentsProcessingServiceMapper.update(existingPayment, input)
                // TODO CUSTOM_REQUIRED: set existingPayment.status to one of FAILED, RETRY_EXHAUSTED
                existingPayment
            }
            ?.let { paymentRepository.save(it) }
            ?.also {
                // emit events
                eventPublisher.onPaymentRetried(eventsMapper.asPaymentRetried(it))
                eventPublisher.onPaymentRetryExhausted(eventsMapper.asPaymentRetryExhausted(it))
            } ?: throw NoSuchElementException("Payment not found")
    }

    override fun capturePayment(input: CapturePaymentInput): Payment {

        log.debug("Request capturePayment: {}", input)

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        val foundPayment: Payment? = emptyList<Payment>().firstOrNull()
        return foundPayment
            ?.let { existingPayment ->
                PaymentTransitions.ensureCanCapturePayment(existingPayment)
                paymentsProcessingServiceMapper.update(existingPayment, input)
                existingPayment.status = PaymentStatus.CAPTURED
                existingPayment
            }
            ?.let { paymentRepository.save(it) }
            ?.also {
                // emit events
                eventPublisher.onPaymentCaptured(eventsMapper.asPaymentCaptured(it))
                eventPublisher.onPaymentCaptureFailed(eventsMapper.asPaymentCaptureFailed(it))
            } ?: throw NoSuchElementException("Payment not found")
    }

    override fun voidPayment(input: VoidPaymentInput): Payment {

        log.debug("Request voidPayment: {}", input)

        // TODO CUSTOM_REQUIRED: load the Payment this transition applies to, e.g. by a correlation key in the input
        val foundPayment: Payment? = emptyList<Payment>().firstOrNull()
        return foundPayment
            ?.let { existingPayment ->
                PaymentTransitions.ensureCanVoidPayment(existingPayment)
                paymentsProcessingServiceMapper.update(existingPayment, input)
                existingPayment.status = PaymentStatus.VOIDED
                existingPayment
            }
            ?.let { paymentRepository.save(it) }
            ?.also {
                // emit events
                eventPublisher.onPaymentVoided(eventsMapper.asPaymentVoided(it))
            } ?: throw NoSuchElementException("Payment not found")
    }
}
