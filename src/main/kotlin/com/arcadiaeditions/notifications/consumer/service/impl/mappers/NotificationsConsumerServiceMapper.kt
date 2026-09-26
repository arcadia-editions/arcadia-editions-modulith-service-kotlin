package com.arcadiaeditions.notifications.consumer.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.*
import com.arcadiaeditions.notifications.consumer.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface NotificationsConsumerServiceMapper {

    companion object {
        val INSTANCE: NotificationsConsumerServiceMapper =
            Mappers.getMapper(NotificationsConsumerServiceMapper::class.java)
    }

    // input mappings
    // SendOrderCancelledNotificationInputnull-Notification sendOrderCancelledNotification

    fun asNotification(input: SendOrderCancelledNotificationInput): Notification

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Notification, input: SendOrderCancelledNotificationInput): Notification

    // SendOrderConfirmationInputnull-Notification sendOrderConfirmation

    fun asNotification(input: SendOrderConfirmationInput): Notification

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Notification, input: SendOrderConfirmationInput): Notification

    // SendStockUnavailableNotificationInputnull-Notification sendStockUnavailableNotification

    fun asNotification(input: SendStockUnavailableNotificationInput): Notification

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Notification, input: SendStockUnavailableNotificationInput): Notification
    // output mappings
}
