package com.arcadiaeditions.notifications.consumer.events.processing.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.notifications.consumer.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asSendOrderConfirmationInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentCaptured
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderConfirmationInput
}
