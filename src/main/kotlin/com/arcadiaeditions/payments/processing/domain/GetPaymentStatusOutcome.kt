package com.arcadiaeditions.payments.processing.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for GetPaymentStatusOutcome. */
enum class GetPaymentStatusOutcome(val value: Int) {

    NO_PAYMENT(1),
    STARTED(2);

    companion object {
        fun fromValue(value: Int): GetPaymentStatusOutcome? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class GetPaymentStatusOutcomeConverter : AttributeConverter<GetPaymentStatusOutcome, Int> {
        override fun convertToDatabaseColumn(attribute: GetPaymentStatusOutcome?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): GetPaymentStatusOutcome? {
            return if (dbData == null) null else GetPaymentStatusOutcome.fromValue(dbData)
        }
    }
}
