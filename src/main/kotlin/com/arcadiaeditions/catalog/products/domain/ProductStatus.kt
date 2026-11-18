package com.arcadiaeditions.catalog.products.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for ProductStatus. */
enum class ProductStatus(val value: Int) {

    DRAFT(1),
    PUBLISHED(2),
    RETIRED(3);

    companion object {
        fun fromValue(value: Int): ProductStatus? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class ProductStatusConverter : AttributeConverter<ProductStatus, Int> {
        override fun convertToDatabaseColumn(attribute: ProductStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): ProductStatus? {
            return if (dbData == null) null else ProductStatus.fromValue(dbData)
        }
    }
}
