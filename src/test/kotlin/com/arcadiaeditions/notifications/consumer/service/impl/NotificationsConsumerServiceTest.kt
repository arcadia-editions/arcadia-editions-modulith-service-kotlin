package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.config.*
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.InMemoryEventPublisher
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.inmemory.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for NotificationsConsumerService. */
class NotificationsConsumerServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val notificationsConsumerService: NotificationsConsumerService = context.notificationsConsumerService()

    val notificationRepository: NotificationRepositoryInMemory = context.notificationRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    val stubNotificationSender: com.arcadiaeditions.notifications.consumer.adapters.stub.StubNotificationSender =
        context.notificationSender() as com.arcadiaeditions.notifications.consumer.adapters.stub.StubNotificationSender

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
        // StubNotificationSender is shared across tests (it's a `protected val` on the config, not
        // recreated by reloadTestData) - reset any `forcedOutcome` a previous test may have set.
        stubNotificationSender.forcedOutcome = null
    }

    // --- sendOrderConfirmation ------------------------------------------------------------------

    @Test
    fun `sendOrderConfirmation sends a new notification and emits one OrderConfirmationSent`() {
        val result =
            notificationsConsumerService.sendOrderConfirmation(
                SendOrderConfirmationInput(orderId = "ORD-NEW-1", customerId = "CUST-009", customerEmail = "new@example.com")
            )

        assertEquals(NotificationStatus.SENT, result.status)
        assertNotNull(result.notificationId)
        assertEquals(NotificationType.ORDER_CONFIRMATION, result.type)
        assertEquals(NotificationChannel.EMAIL, result.channel)
        assertEquals("new@example.com", result.recipient)
        assertNotNull(result.sentAt)
        assertNotNull(result.createdAt)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as OrderConfirmationSent
        assertEquals("ORD-NEW-1", event.orderId)
        assertEquals(result.notificationId, event.notificationId)
    }

    @Test
    fun `sendOrderConfirmation fails when the recipient is undeliverable and emits one NotificationDeliveryFailed`() {
        val result =
            notificationsConsumerService.sendOrderConfirmation(
                SendOrderConfirmationInput(
                    orderId = "ORD-NEW-2",
                    customerId = "CUST-009",
                    customerEmail = "undeliverable@example.com",
                )
            )

        assertEquals(NotificationStatus.FAILED, result.status)
        assertNotNull(result.failedAt)
        assertNotNull(result.failureReason)
        assertNull(result.sentAt)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as NotificationDeliveryFailed
        assertEquals("ORD-NEW-2", event.orderId)
        assertEquals(result.failureReason, event.reason)
    }

    @Test
    fun `sendOrderConfirmation is idempotent when a notification is already SENT for the order`() {
        // NOT-001/ORD-003 is already SENT ORDER_CONFIRMATION (fixture).
        val result =
            notificationsConsumerService.sendOrderConfirmation(
                SendOrderConfirmationInput(orderId = "ORD-003", customerId = "CUST-001", customerEmail = "ada@example.com")
            )

        assertEquals("NOT-001", result.notificationId)
        assertEquals(NotificationStatus.SENT, result.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }

    // --- sendStockUnavailableNotification --------------------------------------------------------

    @Test
    fun `sendStockUnavailableNotification sends a new notification with skus and emits one StockUnavailableNotificationSent`() {
        val result =
            notificationsConsumerService.sendStockUnavailableNotification(
                SendStockUnavailableNotificationInput(
                    orderId = "ORD-NEW-3",
                    customerId = "CUST-009",
                    customerEmail = "new@example.com",
                    unavailableItems =
                        mutableListOf(
                            StockUnavailableItemInput(sku = "AE-EDN-003", requestedQuantity = 1 as Integer, availableQuantity = 0 as Integer)
                        ),
                )
            )

        assertEquals(NotificationStatus.SENT, result.status)
        assertEquals(NotificationType.STOCK_UNAVAILABLE, result.type)
        assertEquals(listOf("AE-EDN-003"), result.skus)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as StockUnavailableNotificationSent
        assertEquals("ORD-NEW-3", event.orderId)
        assertEquals(listOf("AE-EDN-003"), event.skus)
    }

    @Test
    fun `sendStockUnavailableNotification retries a previously FAILED attempt in place and emits one event`() {
        // NOT-004/ORD-007 is already FAILED STOCK_UNAVAILABLE (fixture).
        val result =
            notificationsConsumerService.sendStockUnavailableNotification(
                SendStockUnavailableNotificationInput(
                    orderId = "ORD-007",
                    customerId = "CUST-004",
                    customerEmail = "jorge@example.com",
                    unavailableItems = mutableListOf(StockUnavailableItemInput(sku = "AE-EDN-003", requestedQuantity = null, availableQuantity = null)),
                )
            )

        assertEquals("NOT-004", result.notificationId)
        assertEquals(NotificationStatus.SENT, result.status)
        assertNotNull(result.sentAt)
        assertNull(result.failureReason)

        assertEquals(1, eventPublisher.getEvents().size)
        assertTrue(eventPublisher.getEvents().single() is StockUnavailableNotificationSent)
        assertEquals(1, notificationRepository.findAll().count { it.notificationId == "NOT-004" })
    }

    // --- sendOrderCancelledNotification -----------------------------------------------------------

    @Test
    fun `sendOrderCancelledNotification sends a new notification and emits one OrderCancelledNotificationSent with the reason`() {
        val result =
            notificationsConsumerService.sendOrderCancelledNotification(
                SendOrderCancelledNotificationInput(
                    orderId = "ORD-NEW-4",
                    customerId = "CUST-009",
                    reason = "STOCK_UNAVAILABLE",
                    customerEmail = "new@example.com",
                )
            )

        assertEquals(NotificationStatus.SENT, result.status)
        assertEquals(NotificationType.ORDER_CANCELLED, result.type)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as OrderCancelledNotificationSent
        assertEquals("ORD-NEW-4", event.orderId)
        assertEquals("STOCK_UNAVAILABLE", event.reason)
    }

    @Test
    fun `sendOrderCancelledNotification retries a stalled PENDING attempt in place`() {
        // NOT-003/ORD-006 is PENDING (never SENT nor FAILED) ORDER_CANCELLED (fixture).
        val result =
            notificationsConsumerService.sendOrderCancelledNotification(
                SendOrderCancelledNotificationInput(
                    orderId = "ORD-006",
                    customerId = "CUST-003",
                    reason = "FULFILLMENT_FAILED",
                    customerEmail = "italo@example.com",
                )
            )

        assertEquals("NOT-003", result.notificationId)
        assertEquals(NotificationStatus.SENT, result.status)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as OrderCancelledNotificationSent
        assertEquals("FULFILLMENT_FAILED", event.reason)
    }

    @Test
    fun `sendOrderCancelledNotification is idempotent when a notification is already SENT for the order`() {
        // NOT-002/ORD-005 is already SENT ORDER_CANCELLED (fixture).
        val result =
            notificationsConsumerService.sendOrderCancelledNotification(
                SendOrderCancelledNotificationInput(
                    orderId = "ORD-005",
                    customerId = "CUST-003",
                    reason = "PAYMENT_DECLINED",
                    customerEmail = "italo@example.com",
                )
            )

        assertEquals("NOT-002", result.notificationId)
        assertEquals(NotificationStatus.SENT, result.status)
        assertTrue(eventPublisher.getEvents().isEmpty())
    }
}
