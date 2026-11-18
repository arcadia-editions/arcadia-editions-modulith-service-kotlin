package com.arcadiaeditions.notifications.consumer.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for NotificationChannel. */
enum class NotificationChannel(val value: Int) {

    EMAIL(1),
    SMS(2),
    PUSH(3);

    companion object {
        fun fromValue(value: Int): NotificationChannel? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class NotificationChannelConverter : AttributeConverter<NotificationChannel, Int> {
        override fun convertToDatabaseColumn(attribute: NotificationChannel?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): NotificationChannel? {
            return if (dbData == null) null else NotificationChannel.fromValue(dbData)
        }
    }
}
