package com.arcadiaeditions.payments.processing.adapter.driving.event.shipping.mapper

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.event.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface FulfillmentShippingEventsListenerMapper {

    companion object {
        val INSTANCE: FulfillmentShippingEventsListenerMapper =
            Mappers.getMapper(FulfillmentShippingEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asCapturePaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.CapturePaymentInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asVoidPaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.VoidPaymentInput
}
