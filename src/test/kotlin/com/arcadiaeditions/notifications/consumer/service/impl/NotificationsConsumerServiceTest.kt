package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.config.*
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import com.arcadiaeditions.config.TestClock
import com.arcadiaeditions.notifications.consumer.domain.events.NotificationDeliveryFailed
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

    // C-28 cause: void refused, the e-mail warns the hold may still show
    @Test
    fun sendOrderCancelledNotificationVoidFailedWarnsOfHoldTest() {
        val n =
            notificationsConsumerService.sendOrderCancelledNotification(
                SendOrderCancelledNotificationInput(
                    orderId = "ORD-128",
                    customerId = "CUST-128",
                    reason = "PAYMENT_VOID_FAILED",
                    customerEmail = "customer128@example.com",
                )
            )

        assertEquals(NotificationStatus.SENT, n.status)
        assertTrue(n.body!!.contains("PAYMENT_VOID_FAILED"))
        assertTrue(n.body!!.contains("hold on your card may still show for a few days"))
        assertEquals(n.body, context.emailSender().sent.single().body)
        assertEquals("PAYMENT_VOID_FAILED", (events.single() as OrderCancelledNotificationSent).reason)
    }

    @Test
    fun sendOrderCancelledNotificationOtherReasonNoHoldNoteTest() {
        val n = notificationsConsumerService.sendOrderCancelledNotification(cancelledInput("ORD-021", "CUST-021", "customer21@example.com"))
        assertTrue(!n.body!!.contains("hold"))
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

    private fun assertFailed(n: Notification, type: NotificationType, orderId: String) {
        assertEquals(NotificationStatus.FAILED, n.status)
        assertEquals(TestClock.NOW, n.failedAt)
        assertEquals("mailbox_full", n.failureReason)
        assertNull(n.sentAt)
        assertTrue(notificationRepository.findAll().any { it.orderId == orderId && it.type == type })
        val failed = events.single() as NotificationDeliveryFailed
        assertEquals(n.notificationId, failed.notificationId)
        assertEquals(orderId, failed.orderId)
        assertEquals(type, failed.type)
        assertEquals("mailbox_full", failed.reason)
        assertEquals(TestClock.NOW, failed.failedAt)
        assertTrue(context.emailSender().sent.isEmpty())
    }

    @Test
    fun sendOrderConfirmationDeliveryFailedTest() {
        context.emailSender().refusalReason = "mailbox_full"
        val n = notificationsConsumerService.sendOrderConfirmation(confirmationInput("ORD-005", "CUST-005", "customer05@example.com"))
        assertFailed(n, NotificationType.ORDER_CONFIRMATION, "ORD-005")
    }

    @Test
    fun sendStockUnavailableNotificationDeliveryFailedTest() {
        context.emailSender().refusalReason = "mailbox_full"
        val n = notificationsConsumerService.sendStockUnavailableNotification(stockInput("ORD-107", "CUST-107", "customer107@example.com"))
        assertFailed(n, NotificationType.STOCK_UNAVAILABLE, "ORD-107")
    }

    @Test
    fun sendOrderCancelledNotificationDeliveryFailedTest() {
        context.emailSender().refusalReason = "mailbox_full"
        val n = notificationsConsumerService.sendOrderCancelledNotification(cancelledInput("ORD-021", "CUST-021", "customer21@example.com"))
        assertFailed(n, NotificationType.ORDER_CANCELLED, "ORD-021")
    }

    @Test
    fun redeliveryAfterFailedDoesNothingTest() {
        context.emailSender().refusalReason = "mailbox_full"
        val first = notificationsConsumerService.sendOrderConfirmation(confirmationInput("ORD-005", "CUST-005", "customer05@example.com"))
        val count = notificationRepository.findAll().size
        context.emailSender().refusalReason = null

        val again = notificationsConsumerService.sendOrderConfirmation(confirmationInput("ORD-005", "CUST-005", "customer05@example.com"))

        assertEquals(first.notificationId, again.notificationId)
        assertEquals(NotificationStatus.FAILED, again.status)
        assertEquals(count, notificationRepository.findAll().size)
        assertEquals(1, events.size)
        assertTrue(context.emailSender().sent.isEmpty())
    }
}
