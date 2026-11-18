package com.arcadiaeditions.catalog.inventory.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for ReservationStatus. */
enum class ReservationStatus(val value: Int) {

    RESERVED(1),
    RELEASED(2),
    CONFIRMED(3);

    companion object {
        fun fromValue(value: Int): ReservationStatus? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class ReservationStatusConverter : AttributeConverter<ReservationStatus, Int> {
        override fun convertToDatabaseColumn(attribute: ReservationStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ReservationStatus? {
            return if (dbData == null) null else ReservationStatus.fromValue(dbData)
        }
    }
}
