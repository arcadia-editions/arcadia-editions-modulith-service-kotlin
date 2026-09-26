package com.arcadiaeditions.notifications.consumer.events

import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.domain.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.layered.InfrastructureLayer
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onOrderCancelledNotificationSent(event: OrderCancelledNotificationSent) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onNotificationDeliveryFailed(event: NotificationDeliveryFailed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockUnavailableNotificationSent(event: StockUnavailableNotificationSent) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onOrderConfirmationSent(event: OrderConfirmationSent) {
        applicationEventPublisher.publishEvent(event)
    }
}
