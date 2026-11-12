package com.arcadiaeditions.notifications.consumer.events.checkout.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutEventsListenerMapper {

    companion object {
        val INSTANCE: OrdersCheckoutEventsListenerMapper =
            Mappers.getMapper(OrdersCheckoutEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asSendStockUnavailableNotificationInput(
        event: com.arcadiaeditions.orders.checkout.core.domain.events.StockUnavailable
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendStockUnavailableNotificationInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asSendOrderCancelledNotificationInput(
        event: com.arcadiaeditions.orders.checkout.core.domain.events.OrderCancelled
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderCancelledNotificationInput
}
