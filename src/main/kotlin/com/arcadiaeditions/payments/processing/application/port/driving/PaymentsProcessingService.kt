package com.arcadiaeditions.payments.processing.application.port.driving

import com.arcadiaeditions.payments.processing.application.port.driving.dto.*
import com.arcadiaeditions.payments.processing.domain.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Payment]. */
@org.springframework.modulith.NamedInterface("PaymentsProcessingService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.PrimaryPort
interface PaymentsProcessingService {

    /**
     * Creation only: no-op when a Payment for orderId exists, in any state (supersedes P-01 D). Saves
     * AUTHORIZATION_REQUESTED (attemptCount = 1) and commits before calling the processor, so getPaymentStatus sees a
     * call in flight; then saves the outcome. The processor call times out after 30 seconds and becomes PaymentFailed.
     *
     * With Events: [[PaymentAuthorized, PaymentDeclined, PaymentFailed]].
     */
    fun authorizePayment(input: AuthorizePaymentInput): Payment

    /**
     * No-op unless FAILED and input.attemptCount == attemptCount. Increments attemptCount and commits before calling
     * the processor. The processor call times out after 30 seconds and becomes PaymentFailed.
     *
     * With Events: [[PaymentAuthorized, PaymentDeclined, PaymentFailed]].
     */
    fun reauthorizePayment(input: ReauthorizePaymentInput): Payment

    /**
     * No-op unless FAILED and input.attemptCount == attemptCount. Never increments attemptCount. At most 3 attempts in
     * all (authorize + reauthorize); backoff before the next attempt: 5 seconds before attempt 2, 15 seconds before
     * attempt 3. RETRY_EXHAUSTED at the limit.
     *
     * With Events: [[PaymentRetried, PaymentRetryExhausted]].
     */
    fun retryPayment(input: RetryPaymentInput): Payment

    /**
     * No-op, no event, when CAPTURED, CAPTURE_FAILED or VOIDED (redelivery).
     *
     * With Events: [[PaymentCaptured, PaymentCaptureFailed]].
     */
    fun capturePayment(input: CapturePaymentInput): Payment

    /**
     * FulfillmentFailed and PaymentCaptureFailed void after a failure downstream; OrderConfirmationRejected voids an
     * authorization that arrived after expiry or cancellation. No-op, no event, when there is no Payment or it is not
     * AUTHORIZED or CAPTURE_FAILED (redelivery).
     *
     * With Events: [[PaymentVoided, PaymentVoidFailed]].
     */
    fun voidPayment(input: VoidPaymentInput): Payment?

    /** Read only, for the reservation timer. STARTED for any existing Payment. */
    fun getPaymentStatus(input: GetPaymentStatusInput): GetPaymentStatusResult
}
