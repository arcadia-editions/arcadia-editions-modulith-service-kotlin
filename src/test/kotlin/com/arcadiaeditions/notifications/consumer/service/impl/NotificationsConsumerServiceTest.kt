package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.config.*
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import com.arcadiaeditions.config.TestClock
import com.arcadiaeditions.notifications.consumer.domain.events.OrderCancelledNotificationSent
import com.arcadiaeditions.notifications.consumer.domain.events.OrderConfirmationSent
import com.arcadiaeditions.notifications.consumer.domain.events.StockUnavailableNotificationSent
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

    private fun stockInput(orderId: String, customerId: String, email: String) =
        SendStockUnavailableNotificationInput(
            orderId = orderId,
            customerId = customerId,
            customerEmail = email,
            unavailableItems = mutableListOf(StockUnavailableItemInput("SKU-003", 1 as Integer, 0 as Integer)),
        )

    // C-07 input: StockUnavailable for an order with no notification yet
    @Test
    fun sendStockUnavailableNotificationTest() {
        val before = notificationRepository.findAll().size

        val n =
            notificationsConsumerService.sendStockUnavailableNotification(
                stockInput("ORD-107", "CUST-107", "customer107@example.com")
            )

        assertEquals(NotificationType.STOCK_UNAVAILABLE, n.type)
        assertEquals(NotificationChannel.EMAIL, n.channel)
        assertEquals(NotificationStatus.SENT, n.status)
        assertEquals("customer107@example.com", n.recipient)
        assertEquals("ORD-107", n.orderId)
        assertEquals(listOf("SKU-003"), n.skus)
        assertEquals(TestClock.NOW, n.sentAt)
        assertEquals(TestClock.NOW, n.createdAt)
        assertNotNull(n.notificationId)
        assertEquals(before + 1, notificationRepository.findAll().size)
        val sent = events.single() as StockUnavailableNotificationSent
        assertEquals("ORD-107", sent.orderId)
        assertEquals(listOf("SKU-003"), sent.skus)
        assertEquals(TestClock.NOW, sent.sentAt)
        assertEquals("customer107@example.com", context.emailSender().sent.single().to)
    }

    // C-07: redelivery, already SENT
    @Test
    fun sendStockUnavailableNotificationRedeliveryTest() {
        val before = notificationRepository.findAll().size

        val n =
            notificationsConsumerService.sendStockUnavailableNotification(
                stockInput("ORD-007", "CUST-007", "customer07@example.com")
            )

        assertEquals("NTF-007", n.notificationId)
        assertEquals(before, notificationRepository.findAll().size)
        assertTrue(events.isEmpty())
        assertTrue(context.emailSender().sent.isEmpty())
    }

    private fun cancelledInput(orderId: String, customerId: String, email: String) =
        SendOrderCancelledNotificationInput(
            orderId = orderId,
            customerId = customerId,
            reason = "PAYMENT_DECLINED",
            customerEmail = email,
        )

    // C-21: OrderCancelled, no notification yet
    @Test
    fun sendOrderCancelledNotificationTest() {
        val before = notificationRepository.findAll().size

        val n =
            notificationsConsumerService.sendOrderCancelledNotification(
                cancelledInput("ORD-021", "CUST-021", "customer21@example.com")
            )

        assertEquals(NotificationType.ORDER_CANCELLED, n.type)
        assertEquals(NotificationChannel.EMAIL, n.channel)
        assertEquals(NotificationStatus.SENT, n.status)
        assertEquals("customer21@example.com", n.recipient)
        assertEquals("ORD-021", n.orderId)
        assertTrue(n.body!!.contains("PAYMENT_DECLINED"))
        assertEquals(TestClock.NOW, n.sentAt)
        assertEquals(TestClock.NOW, n.createdAt)
        assertNotNull(n.notificationId)
        assertEquals(before + 1, notificationRepository.findAll().size)
        val sent = events.single() as OrderCancelledNotificationSent
        assertEquals("ORD-021", sent.orderId)
        assertEquals("PAYMENT_DECLINED", sent.reason)
        assertEquals(n.notificationId, sent.notificationId)
        assertEquals(TestClock.NOW, sent.sentAt)
        assertEquals("customer21@example.com", context.emailSender().sent.single().to)
    }

    // C-08: redelivery, already SENT
    @Test
    fun sendOrderCancelledNotificationRedeliveryTest() {
        val before = notificationRepository.findAll().size

        val n =
            notificationsConsumerService.sendOrderCancelledNotification(
                cancelledInput("ORD-008", "CUST-008", "customer08@example.com")
            )

        assertEquals("NTF-008", n.notificationId)
        assertEquals(before, notificationRepository.findAll().size)
        assertTrue(events.isEmpty())
        assertTrue(context.emailSender().sent.isEmpty())
    }
}
