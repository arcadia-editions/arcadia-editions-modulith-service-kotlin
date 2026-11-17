package com.arcadiaeditions.payments.processing.adapter.driven.processor

import com.arcadiaeditions.payments.processing.application.port.driven.processor.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * Deterministic stand-in for the payment processor, driven by the processor's test payment method ids:
 * `pm_card_chargeDeclined` declines, `pm_card_processorError` fails, `pm_card_processorErrorOnce` fails on attempt 1 only, `pm_card_processorErrorThenDecline` fails on attempt 1 and declines the retries, any other (e.g. `pm_card_visa`) is approved.
 * `pm_card_processorNoAnswer` never answers: the call waits until a test calls [releaseStalledAuthorizations] (or
 * [STALL_MAX_SECONDS] pass) and then throws, so the authorization rolls back and payment never resolves.
 * Captures always succeed, keeping the authorization reference, unless a test sets [captureRefusal]; a test may also set [authorizeRefusal] to force every authorization's answer.
 * Voids always succeed, keeping the authorization reference, unless a test sets [voidRefusal].
 * A real processor adapter is a separate task.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["arcadia.fake-adapters.enabled"], havingValue = "true")
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class FakePaymentProcessor : PaymentProcessor {

    companion object {
        const val DECLINED_PAYMENT_METHOD = "pm_card_chargeDeclined"
        const val PROCESSOR_ERROR_PAYMENT_METHOD = "pm_card_processorError"
        /** Fails on the first attempt only (attemptCount 1); approved on the retries. */
        const val PROCESSOR_ERROR_ONCE_PAYMENT_METHOD = "pm_card_processorErrorOnce"
        /** Fails on the first attempt (attemptCount <= 1), declines the retries. */
        const val PROCESSOR_ERROR_THEN_DECLINE_PAYMENT_METHOD = "pm_card_processorErrorThenDecline"
        /** The processor never answers the authorization (payment stalled, C-11). */
        const val NO_ANSWER_PAYMENT_METHOD = "pm_card_processorNoAnswer"
        /** Upper bound of a stalled authorization call, so a test that forgets to release does not hold a thread. */
        const val STALL_MAX_SECONDS = 120L
        const val CARD_DECLINED = "card_declined"
        const val PROCESSOR_TIMEOUT = "processor_timeout"
    }

    /** Test hook: when set, every capture answers this refusal instead of capturing. */
    @Volatile var captureRefusal: PaymentProcessorResult? = null

    /** Test hook: when set, every authorization answers this result instead of the payment method's. */
    @Volatile var authorizeRefusal: PaymentProcessorResult? = null

    /** Test hook: when set, every void answers this refusal instead of voiding. */
    @Volatile var voidRefusal: PaymentProcessorResult? = null

    @Volatile private var stall = CountDownLatch(1)

    /** Order ids whose authorization is waiting for a processor answer that never comes. */
    val stalledOrderIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Test hook: ends every stalled authorization (each one throws, rolling back its transaction). */
    fun releaseStalledAuthorizations() {
        val released = stall
        stall = CountDownLatch(1)
        released.countDown()
    }

    override fun authorize(request: AuthorizationRequest): PaymentProcessorResult =
        authorizeRefusal ?: when (request.paymentMethodId) {
            NO_ANSWER_PAYMENT_METHOD -> {
                val latch = stall
                stalledOrderIds.add(request.orderId)
                try {
                    latch.await(STALL_MAX_SECONDS, TimeUnit.SECONDS)
                } finally {
                    stalledOrderIds.remove(request.orderId)
                }
                throw IllegalStateException("payment processor gave no answer for order ${request.orderId}")
            }
            DECLINED_PAYMENT_METHOD -> PaymentProcessorResult.declined(CARD_DECLINED)
            PROCESSOR_ERROR_PAYMENT_METHOD -> PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
            PROCESSOR_ERROR_ONCE_PAYMENT_METHOD ->
                if (request.attemptCount <= 1) PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
                else PaymentProcessorResult.approved("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
            PROCESSOR_ERROR_THEN_DECLINE_PAYMENT_METHOD ->
                if (request.attemptCount <= 1) PaymentProcessorResult.failed(PROCESSOR_TIMEOUT)
                else PaymentProcessorResult.declined(CARD_DECLINED)
            else -> PaymentProcessorResult.approved("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
        }

    override fun capture(request: CaptureRequest): PaymentProcessorResult =
        captureRefusal
            ?: PaymentProcessorResult.approved(
                request.providerReference ?: ("PSP-AUTH-" + request.orderId.removePrefix("ORD-"))
            )

    override fun void(request: VoidRequest): PaymentProcessorResult =
        voidRefusal
            ?: PaymentProcessorResult.approved(
                request.providerReference ?: ("PSP-VOID-" + request.orderId.removePrefix("ORD-"))
            )
}
