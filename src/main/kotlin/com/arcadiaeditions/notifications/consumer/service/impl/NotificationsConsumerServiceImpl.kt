package com.arcadiaeditions.notifications.consumer.service.impl

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
) : NotificationsConsumerService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val notificationsConsumerServiceMapper: NotificationsConsumerServiceMapper =
        NotificationsConsumerServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun sendOrderConfirmation(input: SendOrderConfirmationInput): Notification {

        log.debug("Request sendOrderConfirmation: {}", input)

        val notification = notificationsConsumerServiceMapper.update(Notification(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onOrderConfirmationSent(eventsMapper.asOrderConfirmationSent(notification))
        eventPublisher.onNotificationDeliveryFailed(eventsMapper.asNotificationDeliveryFailed(notification))
        return notification
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
