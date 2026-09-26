package com.arcadiaeditions.notifications.consumer.events

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onOrderCancelledNotificationSent(event: OrderCancelledNotificationSent) {
        events.add(event)
    }

    override fun onNotificationDeliveryFailed(event: NotificationDeliveryFailed) {
        events.add(event)
    }

    override fun onStockUnavailableNotificationSent(event: StockUnavailableNotificationSent) {
        events.add(event)
    }

    override fun onOrderConfirmationSent(event: OrderConfirmationSent) {
        events.add(event)
    }
}
