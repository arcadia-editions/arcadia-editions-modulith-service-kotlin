package com.arcadiaeditions.notifications.consumer.repository.jpa

import com.arcadiaeditions.notifications.consumer.domain.*
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository

/** Spring Data JPA repository for the Notification entity. */
@Suppress("unused")
@Repository
@org.jmolecules.ddd.annotation.Repository
@org.jmolecules.architecture.layered.InfrastructureLayer
interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByNotificationId(notificationId: String): Notification?

    // G-NC-02: idempotency key for send*Notification is (orderId, type) - not unique, since a
    // legitimate FAILED attempt can be retried in place, but at most one row per (orderId, type)
    // should ever reach SENT.
    fun findByOrderIdAndType(orderId: String, type: NotificationType): Notification?
}
