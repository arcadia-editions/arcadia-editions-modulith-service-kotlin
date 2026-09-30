package com.arcadiaeditions.payments.processing.service.ports

import java.math.BigDecimal

/**
 * Outbound port to the external payment provider (Q-06: a stub adapter is enough for now). The
 * `*ServiceImpl` decides the resulting [Payment][com.arcadiaeditions.payments.processing.domain.Payment]
 * status and event from the returned outcome; it never assumes success.
 */
interface PaymentProvider {

    fun authorize(request: AuthorizeRequest): AuthorizeResult

    fun capture(request: CaptureRequest): CaptureResult

    fun void(request: VoidRequest): VoidResult
}

data class AuthorizeRequest(
    val orderId: String,
    val paymentMethodId: String,
    val amount: BigDecimal,
    val currency: String,
)

enum class AuthorizeOutcome {
    AUTHORIZED,
    DECLINED,
    FAILED,
}

data class AuthorizeResult(
    val outcome: AuthorizeOutcome,
    val providerReference: String? = null,
    val failureReason: String? = null,
)

data class CaptureRequest(val orderId: String, val paymentId: String, val providerReference: String?)

enum class CaptureOutcome {
    CAPTURED,
    FAILED,
}

data class CaptureResult(
    val outcome: CaptureOutcome,
    val providerReference: String? = null,
    val failureReason: String? = null,
)

data class VoidRequest(val orderId: String, val paymentId: String, val providerReference: String?)

enum class VoidOutcome {
    VOIDED,
    FAILED,
}

data class VoidResult(val outcome: VoidOutcome, val failureReason: String? = null)
