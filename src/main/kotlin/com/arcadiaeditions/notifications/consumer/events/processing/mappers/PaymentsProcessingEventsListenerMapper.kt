package com.arcadiaeditions.notifications.consumer.events.processing.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for PaymentsProcessingEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 */
@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper = Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    fun asSendOrderConfirmationInput(
        event: com.arcadiaeditions.payments.processing.domain.events.PaymentCaptured
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderConfirmationInput
}
