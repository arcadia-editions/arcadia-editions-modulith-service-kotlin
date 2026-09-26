package com.arcadiaeditions.catalog.inventory.domain

/** Enum for StockReleaseReason. */
enum class StockReleaseReason {

    PAYMENT_DECLINED,
    PAYMENT_RETRY_EXHAUSTED,
    PAYMENT_VOIDED,
    RESERVATION_EXPIRED,
}
