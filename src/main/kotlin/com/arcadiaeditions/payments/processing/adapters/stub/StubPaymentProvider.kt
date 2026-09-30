package com.arcadiaeditions.payments.processing.adapters.stub

import com.arcadiaeditions.payments.processing.service.ports.AuthorizeOutcome
import com.arcadiaeditions.payments.processing.service.ports.AuthorizeRequest
import com.arcadiaeditions.payments.processing.service.ports.AuthorizeResult
import com.arcadiaeditions.payments.processing.service.ports.CaptureOutcome
import com.arcadiaeditions.payments.processing.service.ports.CaptureRequest
import com.arcadiaeditions.payments.processing.service.ports.CaptureResult
import com.arcadiaeditions.payments.processing.service.ports.PaymentProvider
import com.arcadiaeditions.payments.processing.service.ports.VoidOutcome
import com.arcadiaeditions.payments.processing.service.ports.VoidRequest
import com.arcadiaeditions.payments.processing.service.ports.VoidResult
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * Deterministic default [PaymentProvider] (Q-06). No real provider is wired yet, so this stub is
 * the only bean.
 *
 * Outcome is controlled two ways, checked in this order:
 * 1. The `forced*Outcome` properties, settable by tests, always win when non-null.
 * 2. `AuthorizeRequest.paymentMethodId` magic values: `"pm_card_declined"` → [AuthorizeOutcome.DECLINED],
 *    `"pm_card_fail"` → [AuthorizeOutcome.FAILED]. Any other value → [AuthorizeOutcome.AUTHORIZED].
 *
 * `capture`/`void` have no equivalent input trigger (`CapturePaymentInput`/`VoidPaymentInput` carry
 * no payment-method data), so tests drive them only through `forcedCaptureOutcome`/`forcedVoidOutcome`.
 */
@Component
class StubPaymentProvider : PaymentProvider {

    var forcedAuthorizeOutcome: AuthorizeOutcome? = null
    var forcedCaptureOutcome: CaptureOutcome? = null
    var forcedVoidOutcome: VoidOutcome? = null

    override fun authorize(request: AuthorizeRequest): AuthorizeResult {
        val outcome =
            forcedAuthorizeOutcome
                ?: when (request.paymentMethodId) {
                    DECLINED_PAYMENT_METHOD_ID -> AuthorizeOutcome.DECLINED
                    FAILING_PAYMENT_METHOD_ID -> AuthorizeOutcome.FAILED
                    else -> AuthorizeOutcome.AUTHORIZED
                }
        return when (outcome) {
            AuthorizeOutcome.AUTHORIZED ->
                AuthorizeResult(outcome, providerReference = "stub-auth-${UUID.randomUUID()}")
            AuthorizeOutcome.DECLINED -> AuthorizeResult(outcome, failureReason = "Card declined by issuer")
            AuthorizeOutcome.FAILED -> AuthorizeResult(outcome, failureReason = "Payment provider unavailable")
        }
    }

    override fun capture(request: CaptureRequest): CaptureResult {
        val outcome = forcedCaptureOutcome ?: CaptureOutcome.CAPTURED
        return when (outcome) {
            CaptureOutcome.CAPTURED -> CaptureResult(outcome, providerReference = "stub-cap-${UUID.randomUUID()}")
            CaptureOutcome.FAILED -> CaptureResult(outcome, failureReason = "Capture rejected by provider")
        }
    }

    override fun void(request: VoidRequest): VoidResult {
        val outcome = forcedVoidOutcome ?: VoidOutcome.VOIDED
        return when (outcome) {
            VoidOutcome.VOIDED -> VoidResult(outcome)
            VoidOutcome.FAILED -> VoidResult(outcome, failureReason = "Void rejected by provider")
        }
    }

    companion object {
        const val DECLINED_PAYMENT_METHOD_ID = "pm_card_declined"
        const val FAILING_PAYMENT_METHOD_ID = "pm_card_fail"
    }
}
