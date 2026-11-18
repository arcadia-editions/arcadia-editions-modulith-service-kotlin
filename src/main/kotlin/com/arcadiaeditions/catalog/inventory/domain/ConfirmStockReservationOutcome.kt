package com.arcadiaeditions.catalog.inventory.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for ConfirmStockReservationOutcome. */
enum class ConfirmStockReservationOutcome(val value: Int) {

    CONFIRMED(1),
    NOT_HELD(2);

    companion object {
        fun fromValue(value: Int): ConfirmStockReservationOutcome? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class ConfirmStockReservationOutcomeConverter : AttributeConverter<ConfirmStockReservationOutcome, Int> {
        override fun convertToDatabaseColumn(attribute: ConfirmStockReservationOutcome?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ConfirmStockReservationOutcome? {
            return if (dbData == null) null else ConfirmStockReservationOutcome.fromValue(dbData)
        }
    }
}
