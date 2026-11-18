package com.arcadiaeditions.catalog.inventory.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for ReserveStockOutcome. */
enum class ReserveStockOutcome(val value: Int) {

    RESERVED(1),
    UNAVAILABLE(2);

    companion object {
        fun fromValue(value: Int): ReserveStockOutcome? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class ReserveStockOutcomeConverter : AttributeConverter<ReserveStockOutcome, Int> {
        override fun convertToDatabaseColumn(attribute: ReserveStockOutcome?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReserveStockOutcome? {
            return if (dbData == null) null else ReserveStockOutcome.fromValue(dbData)
        }
    }
}
