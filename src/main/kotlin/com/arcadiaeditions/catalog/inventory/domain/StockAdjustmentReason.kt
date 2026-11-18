package com.arcadiaeditions.catalog.inventory.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for StockAdjustmentReason. */
enum class StockAdjustmentReason(val value: Int) {

    DAMAGED(1),
    LOST(2),
    FOUND(3),
    MANUAL_CORRECTION(4),
    RETURNED(5);

    companion object {
        fun fromValue(value: Int): StockAdjustmentReason? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class StockAdjustmentReasonConverter : AttributeConverter<StockAdjustmentReason, Int> {
        override fun convertToDatabaseColumn(attribute: StockAdjustmentReason?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): StockAdjustmentReason? {
            return if (dbData == null) null else StockAdjustmentReason.fromValue(dbData)
        }
    }
}
