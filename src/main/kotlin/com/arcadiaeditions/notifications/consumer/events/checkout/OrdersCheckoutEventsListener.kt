package com.arcadiaeditions.notifications.consumer.events.checkout

import com.arcadiaeditions.notifications.consumer.domain.events.*
import com.arcadiaeditions.notifications.consumer.events.checkout.mappers.OrdersCheckoutEventsListenerMapper
import com.arcadiaeditions.notifications.consumer.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for OrdersCheckoutZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class OrdersCheckoutEventsListener(
    private val notificationsConsumerService: NotificationsConsumerService,
    private val mapper: OrdersCheckoutEventsListenerMapper,
) {

    @ApplicationModuleListener
    fun onStockUnavailable(event: com.arcadiaeditions.orders.checkout.domain.events.StockUnavailable) {
        notificationsConsumerService.sendStockUnavailableNotification(
            mapper.asSendStockUnavailableNotificationInput(event)
        )
    }

    @ApplicationModuleListener
    fun onOrderCancelled(event: com.arcadiaeditions.orders.checkout.domain.events.OrderCancelled) {
        notificationsConsumerService.sendOrderCancelledNotification(mapper.asSendOrderCancelledNotificationInput(event))
    }
}
