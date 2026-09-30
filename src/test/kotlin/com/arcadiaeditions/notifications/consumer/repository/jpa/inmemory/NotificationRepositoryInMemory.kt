package com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.NotificationRepository
import java.math.*
import java.time.*
import java.util.*

class NotificationRepositoryInMemory : InMemoryJpaRepository<Notification, Long>(), NotificationRepository {
    override fun findByNotificationId(notificationId: String): Notification? =
        findByUniqueField("notificationId", notificationId)

    override fun findByOrderIdAndType(orderId: String, type: NotificationType): Notification? =
        findByField<String>("orderId", orderId).firstOrNull { readField<NotificationType?>(it, "type") == type }
}
