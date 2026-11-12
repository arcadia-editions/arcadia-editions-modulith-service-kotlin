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

    /** With Events: [[PaymentAuthorized, PaymentDeclined, PaymentFailed]]. */
    fun authorizePayment(input: AuthorizePaymentInput): Payment

    /** With Events: [[PaymentRetried, PaymentRetryExhausted]]. */
    fun retryPayment(input: RetryPaymentInput): Payment

    /** With Events: [[PaymentCaptured, PaymentCaptureFailed]]. */
    fun capturePayment(input: CapturePaymentInput): Payment

    /** With Events: [PaymentVoided]. */
    fun voidPayment(input: VoidPaymentInput): Payment
}
