package com.arcadiaeditions.payments.processing.events.processing.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for PaymentsProcessingServiceEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 */
@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingServiceEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingServiceEventsListenerMapper = Mappers.getMapper(PaymentsProcessingServiceEventsListenerMapper::class.java)
    }

    fun asRetryPaymentInput(
        event: PaymentFailed
    ): com.arcadiaeditions.payments.processing.service.dtos.RetryPaymentInput

    fun asVoidPaymentInput(
        event: PaymentCaptureFailed
    ): com.arcadiaeditions.payments.processing.service.dtos.VoidPaymentInput
}
