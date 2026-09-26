package com.arcadiaeditions.notifications.consumer.events

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*

interface EventPublisher {

    fun onOrderCancelledNotificationSent(event: OrderCancelledNotificationSent)

    fun onNotificationDeliveryFailed(event: NotificationDeliveryFailed)

    fun onStockUnavailableNotificationSent(event: StockUnavailableNotificationSent)

    fun onOrderConfirmationSent(event: OrderConfirmationSent)
}
