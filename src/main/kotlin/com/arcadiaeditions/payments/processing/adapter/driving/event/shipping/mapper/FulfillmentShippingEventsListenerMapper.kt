package com.arcadiaeditions.payments.processing.adapter.driving.event.shipping.mapper

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.event.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
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

    /**
     * A-06: the void reason is decided by the event type, not by the fulfillment detail (warehouse_damage stays on the
     * Fulfillment): FulfillmentFailed -> "fulfillment_failed", as data.sql C-10/C-18/C-20.
     */
    @Mapping(target = "reason", constant = "fulfillment_failed")
    fun asVoidPaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.VoidPaymentInput
}
