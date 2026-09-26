package com.arcadiaeditions.payments.processing.events

import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.layered.InfrastructureLayer
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onPaymentCaptureFailed(event: PaymentCaptureFailed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentDeclined(event: PaymentDeclined) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentAuthorized(event: PaymentAuthorized) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentCaptured(event: PaymentCaptured) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentRetried(event: PaymentRetried) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentFailed(event: PaymentFailed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentRetryExhausted(event: PaymentRetryExhausted) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentVoided(event: PaymentVoided) {
        applicationEventPublisher.publishEvent(event)
    }
}
