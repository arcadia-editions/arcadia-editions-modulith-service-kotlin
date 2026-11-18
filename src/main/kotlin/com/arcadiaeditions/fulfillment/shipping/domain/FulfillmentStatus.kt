package com.arcadiaeditions.fulfillment.shipping.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for FulfillmentStatus. */
enum class FulfillmentStatus(val value: Int) {

    SCHEDULED(1),
    READY_TO_SHIP(2),
    FAILED(3),
    CANCELLED(4);

    companion object {
        fun fromValue(value: Int): FulfillmentStatus? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class FulfillmentStatusConverter : AttributeConverter<FulfillmentStatus, Int> {
        override fun convertToDatabaseColumn(attribute: FulfillmentStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): FulfillmentStatus? {
            return if (dbData == null) null else FulfillmentStatus.fromValue(dbData)
        }
    }
}
