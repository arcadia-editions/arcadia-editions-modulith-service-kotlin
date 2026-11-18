package com.arcadiaeditions.notifications.consumer.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for NotificationType. */
enum class NotificationType(val value: Int) {

    ORDER_CONFIRMATION(1),
    STOCK_UNAVAILABLE(2),
    ORDER_CANCELLED(3);

    companion object {
        fun fromValue(value: Int): NotificationType? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class NotificationTypeConverter : AttributeConverter<NotificationType, Int> {
        override fun convertToDatabaseColumn(attribute: NotificationType?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): NotificationType? {
            return if (dbData == null) null else NotificationType.fromValue(dbData)
        }
    }
}
