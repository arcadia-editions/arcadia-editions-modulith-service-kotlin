package com.arcadiaeditions.payments.processing.events.shipping.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for FulfillmentShippingEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 */
@Mapper(uses = [BaseMapper::class])
interface FulfillmentShippingEventsListenerMapper {

    companion object {
        val INSTANCE: FulfillmentShippingEventsListenerMapper = Mappers.getMapper(FulfillmentShippingEventsListenerMapper::class.java)
    }

    fun asCapturePaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled
    ): com.arcadiaeditions.payments.processing.service.dtos.CapturePaymentInput

    fun asVoidPaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed
    ): com.arcadiaeditions.payments.processing.service.dtos.VoidPaymentInput
}
