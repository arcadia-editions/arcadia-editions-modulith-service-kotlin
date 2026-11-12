package com.arcadiaeditions.payments.processing.adapter.driven.event

import com.arcadiaeditions.payments.processing.application.port.driven.event.EventPublisher
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.event.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onPaymentFailed(event: PaymentFailed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentAuthorized(event: PaymentAuthorized) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentRetried(event: PaymentRetried) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentRetryExhausted(event: PaymentRetryExhausted) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentDeclined(event: PaymentDeclined) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentCaptureFailed(event: PaymentCaptureFailed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentCaptured(event: PaymentCaptured) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onPaymentVoided(event: PaymentVoided) {
        applicationEventPublisher.publishEvent(event)
    }
}
