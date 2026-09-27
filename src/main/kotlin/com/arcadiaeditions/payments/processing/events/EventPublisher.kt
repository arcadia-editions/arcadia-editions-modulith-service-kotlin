package com.arcadiaeditions.payments.processing.events

import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*

interface EventPublisher {

    fun onPaymentCaptureFailed(event: PaymentCaptureFailed)

    fun onPaymentDeclined(event: PaymentDeclined)

    fun onPaymentAuthorized(event: PaymentAuthorized)

    fun onPaymentRetried(event: PaymentRetried)

    fun onPaymentFailed(event: PaymentFailed)

    fun onPaymentCaptured(event: PaymentCaptured)

    fun onPaymentRetryExhausted(event: PaymentRetryExhausted)

    fun onPaymentVoided(event: PaymentVoided)
}
