package com.arcadiaeditions.catalog.inventory.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for StockReleaseReason. */
enum class StockReleaseReason(val value: Int) {

    PAYMENT_DECLINED(1),
    PAYMENT_RETRY_EXHAUSTED(2),
    PAYMENT_VOIDED(3),
    PAYMENT_VOID_FAILED(4),
    RESERVATION_EXPIRED(5);

    companion object {
        fun fromValue(value: Int): StockReleaseReason? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class StockReleaseReasonConverter : AttributeConverter<StockReleaseReason, Int> {
        override fun convertToDatabaseColumn(attribute: StockReleaseReason?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): StockReleaseReason? {
            return if (dbData == null) null else StockReleaseReason.fromValue(dbData)
        }
    }
}
