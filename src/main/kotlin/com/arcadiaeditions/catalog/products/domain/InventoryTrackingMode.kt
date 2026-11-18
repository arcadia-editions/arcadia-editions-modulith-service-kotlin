package com.arcadiaeditions.catalog.products.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for InventoryTrackingMode. */
enum class InventoryTrackingMode(val value: Int) {

    QUANTITY(1),
    SERIALIZED(2);

    companion object {
        fun fromValue(value: Int): InventoryTrackingMode? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class InventoryTrackingModeConverter : AttributeConverter<InventoryTrackingMode, Int> {
        override fun convertToDatabaseColumn(attribute: InventoryTrackingMode?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): InventoryTrackingMode? {
            return if (dbData == null) null else InventoryTrackingMode.fromValue(dbData)
        }
    }
}
