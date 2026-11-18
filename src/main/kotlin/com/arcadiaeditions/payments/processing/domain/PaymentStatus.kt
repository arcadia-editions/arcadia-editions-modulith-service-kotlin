package com.arcadiaeditions.payments.processing.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for PaymentStatus. */
enum class PaymentStatus(val value: Int) {

    AUTHORIZATION_REQUESTED(1),
    AUTHORIZED(2),
    DECLINED(3),
    FAILED(4),
    RETRY_EXHAUSTED(5),
    CAPTURED(6),
    CAPTURE_FAILED(7),
    VOIDED(8),
    VOID_FAILED(9);

    companion object {
        fun fromValue(value: Int): PaymentStatus? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class PaymentStatusConverter : AttributeConverter<PaymentStatus, Int> {
        override fun convertToDatabaseColumn(attribute: PaymentStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): PaymentStatus? {
            return if (dbData == null) null else PaymentStatus.fromValue(dbData)
        }
    }
}
