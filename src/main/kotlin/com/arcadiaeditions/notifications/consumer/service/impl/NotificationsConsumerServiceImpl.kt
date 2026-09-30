package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import com.arcadiaeditions.notifications.consumer.service.ports.NotificationSender
import com.arcadiaeditions.notifications.consumer.service.ports.SendOutcome
import com.arcadiaeditions.notifications.consumer.service.ports.SendRequest
import java.math.*
import java.time.*
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Notification]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class NotificationsConsumerServiceImpl(
    private val notificationRepository: NotificationRepository,
    private val eventPublisher: EventPublisher,
    // X-06/G-FT-04: wired now so the constructor is stable; used once the send* methods are implemented.
    private val clock: Clock,
    private val notificationSender: NotificationSender,
) : NotificationsConsumerService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val notificationsConsumerServiceMapper: NotificationsConsumerServiceMapper =
        NotificationsConsumerServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    /**
     * G-NC-02: `sendOrderConfirmation` is a create (`@transition(to: PENDING)`). Idempotency key is
     * (`orderId`, `type`): a notification of this type already `SENT` for this order means
     * `PaymentCaptured` was redelivered and this command already ran - return it as-is, no sender
     * call, no event. A prior `FAILED` attempt for the same (orderId, type) is reused/retried in
     * place rather than creating a second row. Otherwise builds an inline EMAIL template (Q-10),
     * calls [NotificationSender.send] and emits exactly one of OrderConfirmationSent/
     * NotificationDeliveryFailed depending on the outcome.
     */
    override fun sendOrderConfirmation(input: SendOrderConfirmationInput): Notification {

        log.debug("Request sendOrderConfirmation: {}", input)

        val existing = notificationRepository.findByOrderIdAndType(input.orderId, NotificationType.ORDER_CONFIRMATION)
        if (existing != null && existing.status == NotificationStatus.SENT) {
            log.debug("Idempotent redelivery of sendOrderConfirmation for order {}", input.orderId)
            return existing
        }

        val subject = "Your Arcadia Editions order ${input.orderId} is confirmed"
        val body = "Hi, your order ${input.orderId} has been confirmed. Thank you for shopping with Arcadia Editions."

        val notification = prepareNotification(existing, input.orderId, input.customerId, NotificationType.ORDER_CONFIRMATION, skus = emptyList(), recipient = input.customerEmail, subject = subject, body = body)

        return dispatch(
            notification,
            onSent = { eventPublisher.onOrderConfirmationSent(eventsMapper.asOrderConfirmationSent(it)) },
            onFailed = { eventPublisher.onNotificationDeliveryFailed(eventsMapper.asNotificationDeliveryFailed(it)) },
        )
    }

    /**
     * G-NC-02: same shape as [sendOrderConfirmation] (idempotency by `orderId` + `type`), driven by
     * `StockUnavailable`. `skus` are stored on the notification (from `unavailableItems`) so the
     * template and [com.arcadiaeditions.notifications.consumer.domain.events.StockUnavailableNotificationSent]
     * event can list them.
     */
    override fun sendStockUnavailableNotification(input: SendStockUnavailableNotificationInput): Notification {

        log.debug("Request sendStockUnavailableNotification: {}", input)

        val existing = notificationRepository.findByOrderIdAndType(input.orderId, NotificationType.STOCK_UNAVAILABLE)
        if (existing != null && existing.status == NotificationStatus.SENT) {
            log.debug("Idempotent redelivery of sendStockUnavailableNotification for order {}", input.orderId)
            return existing
        }

        val skus = input.unavailableItems.map { it.sku }
        val subject = "Some items in order ${input.orderId} are unavailable"
        val body =
            "Hi, we're sorry - the following items in order ${input.orderId} are no longer available: " +
                skus.joinToString(", ") +
                ". The order has been cancelled."

        val notification = prepareNotification(existing, input.orderId, input.customerId, NotificationType.STOCK_UNAVAILABLE, skus = skus, recipient = input.customerEmail, subject = subject, body = body)

        return dispatch(
            notification,
            onSent = { eventPublisher.onStockUnavailableNotificationSent(eventsMapper.asStockUnavailableNotificationSent(it)) },
            onFailed = { eventPublisher.onNotificationDeliveryFailed(eventsMapper.asNotificationDeliveryFailed(it)) },
        )
    }

    /**
     * G-NC-02: same shape as [sendOrderConfirmation] (idempotency by `orderId` + `type`), driven by
     * `OrderCancelled`. `input.reason` isn't persisted on [Notification] (G-NC-03), so it's passed
     * straight through to [EventsMapper.asOrderCancelledNotificationSent].
     */
    override fun sendOrderCancelledNotification(input: SendOrderCancelledNotificationInput): Notification {

        log.debug("Request sendOrderCancelledNotification: {}", input)

        val existing = notificationRepository.findByOrderIdAndType(input.orderId, NotificationType.ORDER_CANCELLED)
        if (existing != null && existing.status == NotificationStatus.SENT) {
            log.debug("Idempotent redelivery of sendOrderCancelledNotification for order {}", input.orderId)
            return existing
        }

        val subject = "Your Arcadia Editions order ${input.orderId} has been cancelled"
        val body = "Hi, your order ${input.orderId} has been cancelled. Reason: ${input.reason}."

        val notification = prepareNotification(existing, input.orderId, input.customerId, NotificationType.ORDER_CANCELLED, skus = emptyList(), recipient = input.customerEmail, subject = subject, body = body)

        return dispatch(
            notification,
            onSent = { eventPublisher.onOrderCancelledNotificationSent(eventsMapper.asOrderCancelledNotificationSent(it, input.reason)) },
            onFailed = { eventPublisher.onNotificationDeliveryFailed(eventsMapper.asNotificationDeliveryFailed(it)) },
        )
    }

    /**
     * Fills the developer-owned fields common to every `send*` command: generates `notificationId`
     * once (kept across a retried FAILED attempt), sets `type`/`channel`/`recipient`/`subject`/
     * `body`, and `createdAt` once. [existing] is the prior row for this (orderId, type) if this is
     * a retry of a previously FAILED attempt, otherwise a new [Notification].
     */
    private fun prepareNotification(
        existing: Notification?,
        orderId: String,
        customerId: String,
        type: NotificationType,
        skus: List<String>,
        recipient: String,
        subject: String,
        body: String,
    ): Notification {
        val notification = existing ?: Notification()
        if (notification.notificationId == null) notification.notificationId = "NOT-${UUID.randomUUID()}"
        notification.customerId = customerId
        notification.orderId = orderId
        notification.skus = skus.toMutableList()
        notification.type = type
        notification.channel = NotificationChannel.EMAIL
        notification.recipient = recipient
        notification.subject = subject
        notification.body = body
        if (notification.createdAt == null) notification.createdAt = Instant.now(clock)
        return notification
    }

    /**
     * Calls [NotificationSender.send] and applies exactly one outcome branch (SENT/FAILED), saving
     * either way, then invokes exactly one of [onSent]/[onFailed] to publish exactly one event for
     * that branch.
     */
    private fun dispatch(
        notification: Notification,
        onSent: (Notification) -> Unit,
        onFailed: (Notification) -> Unit,
    ): Notification {
        val now = Instant.now(clock)
        val result =
            notificationSender.send(
                SendRequest(recipient = notification.recipient!!, subject = notification.subject!!, body = notification.body!!)
            )
        return when (result.outcome) {
            SendOutcome.SENT -> {
                notification.status = NotificationStatus.SENT
                notification.sentAt = now
                notification.failureReason = null
                val saved = notificationRepository.save(notification)
                onSent(saved)
                saved
            }
            SendOutcome.FAILED -> {
                notification.status = NotificationStatus.FAILED
                notification.failedAt = now
                notification.failureReason = result.failureReason
                val saved = notificationRepository.save(notification)
                onFailed(saved)
                saved
            }
        }
    }
}
