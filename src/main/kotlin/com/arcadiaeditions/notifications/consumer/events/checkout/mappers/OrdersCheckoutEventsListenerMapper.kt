package com.arcadiaeditions.notifications.consumer.events.checkout.mappers

import com.arcadiaeditions.notifications.consumer.domain.events.*

/**
 * Regenerated mapping contract for OrdersCheckoutEventsListener. Customize the mappings in the generated-once
 * OrdersCheckoutEventsListenerMapStructMapper.
 */
interface OrdersCheckoutEventsListenerMapper {

    fun asSendStockUnavailableNotificationInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.StockUnavailable
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendStockUnavailableNotificationInput

    fun asSendOrderCancelledNotificationInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.OrderCancelled
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderCancelledNotificationInput
}
