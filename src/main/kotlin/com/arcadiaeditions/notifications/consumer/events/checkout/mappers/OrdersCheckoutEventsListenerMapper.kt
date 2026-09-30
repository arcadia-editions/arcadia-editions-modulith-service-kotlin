package com.arcadiaeditions.notifications.consumer.events.checkout.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for OrdersCheckoutEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 */
@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutEventsListenerMapper {

    companion object {
        val INSTANCE: OrdersCheckoutEventsListenerMapper = Mappers.getMapper(OrdersCheckoutEventsListenerMapper::class.java)
    }

    fun asSendStockUnavailableNotificationInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.StockUnavailable
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendStockUnavailableNotificationInput

    fun asSendOrderCancelledNotificationInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.OrderCancelled
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderCancelledNotificationInput
}
