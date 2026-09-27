package com.arcadiaeditions.notifications.consumer.events

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*

interface EventPublisher {

    fun onStockUnavailableNotificationSent(event: StockUnavailableNotificationSent)

    fun onOrderCancelledNotificationSent(event: OrderCancelledNotificationSent)

    fun onOrderConfirmationSent(event: OrderConfirmationSent)

    fun onNotificationDeliveryFailed(event: NotificationDeliveryFailed)
}
