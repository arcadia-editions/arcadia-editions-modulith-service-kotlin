package com.arcadiaeditions.notifications.consumer.service.impl

import com.arcadiaeditions.notifications.consumer.*
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.*
import com.arcadiaeditions.notifications.consumer.repository.jpa.*
import com.arcadiaeditions.notifications.consumer.service.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import com.arcadiaeditions.notifications.consumer.service.impl.mappers.*
import java.math.*
import java.time.*
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
    private val emailSender: EmailSender,
    private val clock: Clock,
) : NotificationsConsumerService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val notificationsConsumerServiceMapper: NotificationsConsumerServiceMapper =
        NotificationsConsumerServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun sendOrderConfirmation(input: SendOrderConfirmationInput): Notification {

        log.debug("Request sendOrderConfirmation: {}", input)

        // redelivery of PaymentCaptured: one confirmation per order
        notificationRepository.findByOrderIdAndType(input.orderId, NotificationType.ORDER_CONFIRMATION)?.let {
            return it
        }

        val now = Instant.now(clock)
        val notification = notificationsConsumerServiceMapper.update(Notification(), input)
        notification.notificationId = "NTF-" + java.util.UUID.randomUUID()
        notification.type = NotificationType.ORDER_CONFIRMATION
        notification.channel = NotificationChannel.EMAIL
        notification.recipient = input.customerEmail
        notification.subject = "Your Arcadia Editions order is confirmed"
        notification.body = "Your Arcadia Editions order is confirmed: ${input.orderId}"
        notification.createdAt = now

        val result = emailSender.send(EmailMessage(input.customerEmail, notification.subject!!, notification.body!!))
        // the NotificationDeliveryFailed branch (FAILED) is a later task
        if (!result.sent) throw UnsupportedOperationException("e-mail delivery refused: not implemented")
        // A-01 (P-02): SENT is set here, as the outcome of OrderConfirmationSent
        notification.status = NotificationStatus.SENT
        notification.sentAt = now
        val saved = notificationRepository.save(notification)

        eventPublisher.onOrderConfirmationSent(eventsMapper.asOrderConfirmationSent(saved))
        return saved
    }

    override fun sendStockUnavailableNotification(input: SendStockUnavailableNotificationInput): Notification {

        log.debug("Request sendStockUnavailableNotification: {}", input)

        val notification = notificationsConsumerServiceMapper.update(Notification(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onStockUnavailableNotificationSent(eventsMapper.asStockUnavailableNotificationSent(notification))
        eventPublisher.onNotificationDeliveryFailed(eventsMapper.asNotificationDeliveryFailed(notification))
        return notification
    }

    override fun sendOrderCancelledNotification(input: SendOrderCancelledNotificationInput): Notification {

        log.debug("Request sendOrderCancelledNotification: {}", input)

        val notification = notificationsConsumerServiceMapper.update(Notification(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onOrderCancelledNotificationSent(eventsMapper.asOrderCancelledNotificationSent(notification))
        eventPublisher.onNotificationDeliveryFailed(eventsMapper.asNotificationDeliveryFailed(notification))
        return notification
    }
}
