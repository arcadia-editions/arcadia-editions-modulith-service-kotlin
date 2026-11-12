package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.config.*
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import com.arcadiaeditions.config.TestClock
import com.arcadiaeditions.notifications.consumer.domain.events.OrderConfirmationSent
import java.time.*
import org.junit.jupiter.api.Assertions.*
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

    private val events
        get() = context.eventPublisher().getEvents()

    private fun confirmationInput(orderId: String, customerId: String, email: String) =
        SendOrderConfirmationInput(orderId = orderId, customerId = customerId, customerEmail = email)

    @Test
    fun servicesUseTheFixedClockTest() {
        assertEquals(TestClock.NOW, context.clock.instant())
    }

    // C-05: PaymentCaptured, no notification yet
    @Test
    fun sendOrderConfirmationTest() {
        val before = notificationRepository.findAll().size

        val n =
            notificationsConsumerService.sendOrderConfirmation(
                confirmationInput("ORD-005", "CUST-005", "customer05@example.com")
            )

        assertEquals(NotificationType.ORDER_CONFIRMATION, n.type)
        assertEquals(NotificationChannel.EMAIL, n.channel)
        assertEquals(NotificationStatus.SENT, n.status)
        assertEquals("customer05@example.com", n.recipient)
        assertEquals("ORD-005", n.orderId)
        assertEquals(TestClock.NOW, n.sentAt)
        assertEquals(TestClock.NOW, n.createdAt)
        assertNotNull(n.notificationId)
        assertEquals(before + 1, notificationRepository.findAll().size)
        val sent = events.single() as OrderConfirmationSent
        assertEquals("ORD-005", sent.orderId)
        assertEquals(TestClock.NOW, sent.sentAt)
        assertEquals("customer05@example.com", context.emailSender().sent.single().to)
    }

    // C-06: redelivery, already SENT
    @Test
    fun sendOrderConfirmationRedeliveryTest() {
        val before = notificationRepository.findAll().size

        val n =
            notificationsConsumerService.sendOrderConfirmation(
                confirmationInput("ORD-006", "CUST-006", "customer06@example.com")
            )

        assertEquals("NTF-006", n.notificationId)
        assertEquals(before, notificationRepository.findAll().size)
        assertTrue(events.isEmpty())
        assertTrue(context.emailSender().sent.isEmpty())
    }

    @Test
    fun sendStockUnavailableNotificationTest() { // TODO: implement this test
    }

    @Test
    fun sendOrderCancelledNotificationTest() { // TODO: implement this test
    }
}
