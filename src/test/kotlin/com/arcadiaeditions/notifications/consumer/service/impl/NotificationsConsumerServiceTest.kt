package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.config.*
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import java.time.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for NotificationsConsumerService. */
class NotificationsConsumerServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val notificationsConsumerService: NotificationsConsumerService = context.notificationsConsumerService()

    val notificationRepository: NotificationRepositoryInMemory = context.notificationRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun sendOrderConfirmationTest() { // TODO: implement this test
    }

    @Test
    fun sendStockUnavailableNotificationTest() { // TODO: implement this test
    }

    @Test
    fun sendOrderCancelledNotificationTest() { // TODO: implement this test
    }
}
