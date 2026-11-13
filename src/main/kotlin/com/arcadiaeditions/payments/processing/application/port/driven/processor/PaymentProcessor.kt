package com.arcadiaeditions.payments.processing.application.port.driven.processor

import java.math.BigDecimal

/**
 * Outbound port to the payment processor (PSP). One operation per processor call of the payment lifecycle:
 * authorize now; capture and void follow the same request/result shape.
 */
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.SecondaryPort
interface PaymentProcessor {

    /** Places an authorization hold on the payment method. Never throws for a processor outcome: see [outcome]. */
    fun authorize(request: AuthorizationRequest): PaymentProcessorResult

    /** Captures a previously authorized hold. Never throws for a processor outcome: see [outcome]. */
    fun capture(request: CaptureRequest): PaymentProcessorResult
}

data class AuthorizationRequest(
    val orderId: String,
    val customerId: String,
    val paymentMethodId: String,
    val amount: BigDecimal,
    val currency: String,
    /** Attempt being made (1 on the first authorization, more on PaymentRetried). */
    val attemptCount: Int = 1,
)

data class CaptureRequest(
    val orderId: String,
    /** Reference of the authorization hold to capture (Payment.providerReference). */
    val providerReference: String?,
    val amount: BigDecimal,
    val currency: String,
)

/** What the processor answered to one call. */
enum class PaymentProcessorOutcome {
    /** The processor accepted the operation; [PaymentProcessorResult.providerReference] identifies it. */
    APPROVED,
    /** The processor rejected the operation outright (no retry). */
    DECLINED,
    /** A technical error on our side or the processor's (retryable). */
    FAILED,
}

data class PaymentProcessorResult(
    val outcome: PaymentProcessorOutcome,
    /** Processor reference of the operation, set when [outcome] is APPROVED. */
    val providerReference: String? = null,
    /** Reason code when [outcome] is DECLINED or FAILED (A-06: card_declined, processor_timeout, ...). */
    val reason: String? = null,
) {
    companion object {
        fun approved(providerReference: String) = PaymentProcessorResult(PaymentProcessorOutcome.APPROVED, providerReference)

        fun declined(reason: String) = PaymentProcessorResult(PaymentProcessorOutcome.DECLINED, reason = reason)

        fun failed(reason: String) = PaymentProcessorResult(PaymentProcessorOutcome.FAILED, reason = reason)
    }
}
