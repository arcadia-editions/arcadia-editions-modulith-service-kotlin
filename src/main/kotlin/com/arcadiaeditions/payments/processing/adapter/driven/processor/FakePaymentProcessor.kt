package com.arcadiaeditions.payments.processing.adapter.driven.processor

import com.arcadiaeditions.payments.processing.application.port.driven.processor.*
import org.springframework.stereotype.Component

/**
 * Deterministic stand-in for the payment processor, driven by the processor's test payment method ids:
 * `pm_card_chargeDeclined` declines, `pm_card_processorError` fails, any other (e.g. `pm_card_visa`) is approved.
 * A real processor adapter is a separate task.
 */
@Component
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class FakePaymentProcessor : PaymentProcessor {

    companion object {
        const val DECLINED_PAYMENT_METHOD = "pm_card_chargeDeclined"
        const val PROCESSOR_ERROR_PAYMENT_METHOD = "pm_card_processorError"
        const val CARD_DECLINED = "card_declined"
        const val PROCESSOR_TIMEOUT = "processor_timeout"
    }

    override fun authorize(request: AuthorizationRequest): PaymentProcessorResult =
        when (request.paymentMethodId) {
            DECLINED_PAYMENT_METHOD -> PaymentProcessorResult.declined(CARD_DECLINED)
            PROCESSOR_ERROR_PAYMENT_METHOD -> PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
            else -> PaymentProcessorResult.approved("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
        }
}
