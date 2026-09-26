package com.arcadiaeditions.notifications.consumer.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asNotificationDeliveryFailed(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.NotificationDeliveryFailed

    fun asStockUnavailableNotificationSent(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.StockUnavailableNotificationSent

    fun asOrderConfirmationSent(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.OrderConfirmationSent

    fun asOrderCancelledNotificationSent(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.OrderCancelledNotificationSent
}
