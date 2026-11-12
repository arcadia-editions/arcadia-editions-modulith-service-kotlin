package com.arcadiaeditions.payments.processing.adapter.driven.event

import com.arcadiaeditions.payments.processing.application.port.driven.event.EventPublisher
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.event.*

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onPaymentFailed(event: PaymentFailed) {
        events.add(event)
    }

    override fun onPaymentAuthorized(event: PaymentAuthorized) {
        events.add(event)
    }

    override fun onPaymentRetried(event: PaymentRetried) {
        events.add(event)
    }

    override fun onPaymentRetryExhausted(event: PaymentRetryExhausted) {
        events.add(event)
    }

    override fun onPaymentDeclined(event: PaymentDeclined) {
        events.add(event)
    }

    override fun onPaymentCaptureFailed(event: PaymentCaptureFailed) {
        events.add(event)
    }

    override fun onPaymentCaptured(event: PaymentCaptured) {
        events.add(event)
    }

    override fun onPaymentVoided(event: PaymentVoided) {
        events.add(event)
    }
}
