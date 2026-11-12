package com.arcadiaeditions.orders.checkout.adapters.events.processing.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asConfirmOrderInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentAuthorized
    ): com.arcadiaeditions.orders.checkout.core.inbound.dtos.ConfirmOrderInput
}
