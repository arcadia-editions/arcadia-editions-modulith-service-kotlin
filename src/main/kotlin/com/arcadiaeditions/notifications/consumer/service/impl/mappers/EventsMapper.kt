package com.arcadiaeditions.notifications.consumer.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    /**
     * G-NC-03: [com.arcadiaeditions.notifications.consumer.domain.events.NotificationDeliveryFailed.reason]
     * has no matching field on [Notification] by name - it's sourced from `failureReason`, which
     * each `send*` method sets on the FAILED branch.
     */
    @Mapping(target = "reason", source = "failureReason")
    fun asNotificationDeliveryFailed(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.NotificationDeliveryFailed

    fun asStockUnavailableNotificationSent(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.StockUnavailableNotificationSent

    fun asOrderConfirmationSent(
        notification: Notification
    ): com.arcadiaeditions.notifications.consumer.domain.events.OrderConfirmationSent

    /**
     * G-NC-03: [com.arcadiaeditions.notifications.consumer.domain.events.OrderCancelledNotificationSent.reason]
     * has no source on [Notification] at all (the entity has no `reason` field) - it's the
     * cancellation reason from [com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderCancelledNotificationInput],
     * passed through as an extra parameter, same pattern as payments-processing's EventsMapper
     * (G-PP-06).
     */
    fun asOrderCancelledNotificationSent(
        notification: Notification,
        reason: String?,
    ): com.arcadiaeditions.notifications.consumer.domain.events.OrderCancelledNotificationSent
}
