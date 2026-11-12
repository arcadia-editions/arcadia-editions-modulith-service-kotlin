package com.arcadiaeditions.payments.processing.adapter.driving.event.processing.mapper

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.event.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingServiceEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingServiceEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingServiceEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asAuthorizePaymentInput(
        event: PaymentRetried
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.AuthorizePaymentInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asRetryPaymentInput(
        event: PaymentFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.RetryPaymentInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asVoidPaymentInput(
        event: PaymentCaptureFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.VoidPaymentInput
}
