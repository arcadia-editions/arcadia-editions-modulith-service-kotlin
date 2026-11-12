package com.arcadiaeditions.payments.processing.application.port.driven.event

import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.event.*

@org.jmolecules.architecture.hexagonal.SecondaryPort
interface EventPublisher {

    fun onPaymentFailed(event: PaymentFailed)

    fun onPaymentAuthorized(event: PaymentAuthorized)

    fun onPaymentRetried(event: PaymentRetried)

    fun onPaymentRetryExhausted(event: PaymentRetryExhausted)

    fun onPaymentDeclined(event: PaymentDeclined)

    fun onPaymentCaptureFailed(event: PaymentCaptureFailed)

    fun onPaymentCaptured(event: PaymentCaptured)

    fun onPaymentVoided(event: PaymentVoided)
}
