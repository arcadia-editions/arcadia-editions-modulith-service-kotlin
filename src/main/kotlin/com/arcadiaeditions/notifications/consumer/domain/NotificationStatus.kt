package com.arcadiaeditions.notifications.consumer.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Enum for NotificationStatus. */
enum class NotificationStatus(val value: Int) {

    PENDING(1),
    SENT(2),
    FAILED(3);

    companion object {
        fun fromValue(value: Int): NotificationStatus? {
            return values().find { it.value == value }
        }
    }

    @Converter
    class NotificationStatusConverter : AttributeConverter<NotificationStatus, Int> {
        override fun convertToDatabaseColumn(attribute: NotificationStatus?): Int? {
            return attribute?.value
        }

        override fun convertToEntityAttribute(dbData: Int?): NotificationStatus? {
            return if (dbData == null) null else NotificationStatus.fromValue(dbData)
        }
    }
}
