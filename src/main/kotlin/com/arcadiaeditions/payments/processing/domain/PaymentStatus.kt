package com.arcadiaeditions.payments.processing.domain

/** Enum for PaymentStatus. */
enum class PaymentStatus {

    AUTHORIZATION_REQUESTED,
    AUTHORIZED,
    DECLINED,
    FAILED,
    RETRY_EXHAUSTED,
    CAPTURED,
    VOIDED,
}
