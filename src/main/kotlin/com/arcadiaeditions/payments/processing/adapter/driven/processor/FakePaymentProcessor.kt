package com.arcadiaeditions.payments.processing.adapter.driven.processor

import com.arcadiaeditions.payments.processing.application.port.driven.processor.*
import org.springframework.stereotype.Component

/**
 * Deterministic stand-in for the payment processor, driven by the processor's test payment method ids:
 * `pm_card_chargeDeclined` declines, `pm_card_processorError` fails, `pm_card_processorErrorOnce` fails on attempt 1 only, any other (e.g. `pm_card_visa`) is approved.
 * Captures always succeed, keeping the authorization reference, unless a test sets [captureRefusal].
 * A real processor adapter is a separate task.
 */
@Component
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class FakePaymentProcessor : PaymentProcessor {

    companion object {
        const val DECLINED_PAYMENT_METHOD = "pm_card_chargeDeclined"
        const val PROCESSOR_ERROR_PAYMENT_METHOD = "pm_card_processorError"
        /** Fails on the first attempt only (attemptCount 1); approved on the retries. */
        const val PROCESSOR_ERROR_ONCE_PAYMENT_METHOD = "pm_card_processorErrorOnce"
        const val CARD_DECLINED = "card_declined"
        const val PROCESSOR_TIMEOUT = "processor_timeout"
    }

    /** Test hook: when set, every capture answers this refusal instead of capturing. */
    @Volatile var captureRefusal: PaymentProcessorResult? = null

    override fun authorize(request: AuthorizationRequest): PaymentProcessorResult =
        when (request.paymentMethodId) {
            DECLINED_PAYMENT_METHOD -> PaymentProcessorResult.declined(CARD_DECLINED)
            PROCESSOR_ERROR_PAYMENT_METHOD -> PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
            PROCESSOR_ERROR_ONCE_PAYMENT_METHOD ->
                if (request.attemptCount <= 1) PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
                else PaymentProcessorResult.approved("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
            else -> PaymentProcessorResult.approved("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
        }

    override fun capture(request: CaptureRequest): PaymentProcessorResult =
        captureRefusal
            ?: PaymentProcessorResult.approved(
                request.providerReference ?: ("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
            )
}
