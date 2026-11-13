package com.arcadiaeditions.payments.processing.adapter.driving.event.processing.mapper

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.event.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingServiceEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingServiceEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingServiceEventsListenerMapper::class.java)
    }

    /**
     * P-01 D: PaymentRetried carries only orderId and customerId; the service takes the rest from the FAILED Payment
     * of the order, so these placeholders are never read (the DTO fields are non-null).
     */
    @Mapping(target = "customerEmail", constant = "")
    @Mapping(target = "amount", expression = "java(java.math.BigDecimal.ZERO)")
    @Mapping(target = "currency", constant = "")
    @Mapping(target = "paymentMethodId", constant = "")
    fun asAuthorizePaymentInput(
        event: PaymentRetried
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.AuthorizePaymentInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asRetryPaymentInput(
        event: PaymentFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.RetryPaymentInput

    /** On purpose, PaymentCaptureFailed.reason (e.g. capture_window_closed) passes through to the void, as data.sql C-17. */
    fun asVoidPaymentInput(
        event: PaymentCaptureFailed
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.VoidPaymentInput
}
