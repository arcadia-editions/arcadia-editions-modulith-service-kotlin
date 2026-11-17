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
    fun asReauthorizePaymentInput(
        event: PaymentRetried
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.ReauthorizePaymentInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asRetryPaymentInput(
        event: PaymentFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.RetryPaymentInput

    /** On purpose, PaymentCaptureFailed.reason (e.g. capture_window_closed) passes through to the void, as data.sql C-17. */
    fun asVoidPaymentInput(
        event: PaymentCaptureFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.VoidPaymentInput
}
