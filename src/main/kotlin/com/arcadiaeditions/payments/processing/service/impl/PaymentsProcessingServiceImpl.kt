package com.arcadiaeditions.payments.processing.service.impl

import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.*
import com.arcadiaeditions.payments.processing.repository.jpa.*
import com.arcadiaeditions.payments.processing.service.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import com.arcadiaeditions.payments.processing.service.impl.mappers.*
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
@org.jmolecules.architecture.layered.ApplicationLayer
open class PaymentsProcessingServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val eventPublisher: EventPublisher,
) : PaymentsProcessingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val paymentsProcessingServiceMapper: PaymentsProcessingServiceMapper =
        PaymentsProcessingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun authorizePayment(input: AuthorizePaymentInput): Payment {

        log.debug("Request authorizePayment: {}", input)

        val payment = paymentsProcessingServiceMapper.update(Payment(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onPaymentAuthorized(eventsMapper.asPaymentAuthorized(payment))
        eventPublisher.onPaymentDeclined(eventsMapper.asPaymentDeclined(payment))
        eventPublisher.onPaymentFailed(eventsMapper.asPaymentFailed(payment))
        return payment
    }

    override fun retryPayment(input: RetryPaymentInput): Payment {

        log.debug("Request retryPayment: {}", input)

        val payment = paymentsProcessingServiceMapper.update(Payment(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onPaymentRetried(eventsMapper.asPaymentRetried(payment))
        eventPublisher.onPaymentRetryExhausted(eventsMapper.asPaymentRetryExhausted(payment))
        return payment
    }

    override fun capturePayment(input: CapturePaymentInput): Payment {

        log.debug("Request capturePayment: {}", input)

        val payment = paymentsProcessingServiceMapper.update(Payment(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onPaymentCaptured(eventsMapper.asPaymentCaptured(payment))
        eventPublisher.onPaymentCaptureFailed(eventsMapper.asPaymentCaptureFailed(payment))
        return payment
    }

    override fun voidPayment(input: VoidPaymentInput): Payment {

        log.debug("Request voidPayment: {}", input)

        val payment = paymentsProcessingServiceMapper.update(Payment(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onPaymentVoided(eventsMapper.asPaymentVoided(payment))
        return payment
    }
}
