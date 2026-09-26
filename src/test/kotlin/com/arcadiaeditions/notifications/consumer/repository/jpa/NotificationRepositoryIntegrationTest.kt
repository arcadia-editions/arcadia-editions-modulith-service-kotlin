package com.arcadiaeditions.notifications.consumer.repository.jpa

import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.persistence.EntityManager
import java.time.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class NotificationRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var notificationRepository: NotificationRepository

    @Test
    fun findAllTest() {
        val results = notificationRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val notification =
            notificationRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(notification.id)
        Assertions.assertNotNull(notification.version)
    }

    @Test
    fun saveTest() {
        val notification = Notification()
        notification.notificationId = ""
        notification.customerId = ""
        notification.orderId = ""
        notification.sku = ""
        notification.type = NotificationType.values()[0]
        notification.channel = NotificationChannel.values()[0]
        notification.status = NotificationStatus.values()[0]
        notification.subject = ""
        notification.body = ""
        notification.recipient = ""
        notification.createdAt = Instant.now()
        notification.sentAt = Instant.now()
        notification.failedAt = Instant.now()
        notification.failureReason = ""

        // Persist aggregate root
        val created = notificationRepository.save(notification)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val notification =
            notificationRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        notification.notificationId = ""
        notification.customerId = ""
        notification.orderId = ""
        notification.sku = ""
        notification.type = NotificationType.values()[0]
        notification.channel = NotificationChannel.values()[0]
        notification.status = NotificationStatus.values()[0]
        notification.subject = ""
        notification.body = ""
        notification.recipient = ""
        notification.createdAt = Instant.now()
        notification.sentAt = Instant.now()
        notification.failedAt = Instant.now()
        notification.failureReason = ""

        val updated = notificationRepository.save(notification)
        Assertions.assertEquals("", updated.notificationId)
        Assertions.assertEquals("", updated.customerId)
        Assertions.assertEquals("", updated.orderId)
        Assertions.assertEquals("", updated.sku)
        Assertions.assertEquals(NotificationType.values()[0], updated.type)
        Assertions.assertEquals(NotificationChannel.values()[0], updated.channel)
        Assertions.assertEquals(NotificationStatus.values()[0], updated.status)
        Assertions.assertEquals("", updated.subject)
        Assertions.assertEquals("", updated.body)
        Assertions.assertEquals("", updated.recipient)
        Assertions.assertEquals(Instant.now(), updated.createdAt)
        Assertions.assertEquals(Instant.now(), updated.sentAt)
        Assertions.assertEquals(Instant.now(), updated.failedAt)
        Assertions.assertEquals("", updated.failureReason)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        notificationRepository.deleteById(id)
        val notFound = notificationRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
