package com.arcadiaeditions.notifications.consumer.service

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Notification]. */
@org.springframework.modulith.NamedInterface("NotificationsConsumerService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
interface NotificationsConsumerService {

    /** With Events: [[OrderConfirmationSent, NotificationDeliveryFailed]]. */
    fun sendOrderConfirmation(input: SendOrderConfirmationInput): Notification

    /** With Events: [[StockUnavailableNotificationSent, NotificationDeliveryFailed]]. */
    fun sendStockUnavailableNotification(input: SendStockUnavailableNotificationInput): Notification

    /** With Events: [[OrderCancelledNotificationSent, NotificationDeliveryFailed]]. */
    fun sendOrderCancelledNotification(input: SendOrderCancelledNotificationInput): Notification
}
