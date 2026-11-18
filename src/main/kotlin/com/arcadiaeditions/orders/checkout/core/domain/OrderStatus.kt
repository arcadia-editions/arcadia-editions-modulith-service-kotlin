package com.arcadiaeditions.orders.checkout.core.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for OrderStatus. */
enum class OrderStatus(val value: Int) {

    CREATED(1),
    CONFIRMED(2),
    CANCELLED(3);

    companion object {
        fun fromValue(value: Int): OrderStatus? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class OrderStatusConverter : AttributeConverter<OrderStatus, Int> {
        override fun convertToDatabaseColumn(attribute: OrderStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): OrderStatus? {
            return if (dbData == null) null else OrderStatus.fromValue(dbData)
        }
    }
}
