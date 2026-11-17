package com.arcadiaeditions.fulfillment.shipping.processing.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asRecordPaymentCapturedInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentCaptured
    ): com.arcadiaeditions.fulfillment.shipping.dtos.RecordPaymentCapturedInput
}
