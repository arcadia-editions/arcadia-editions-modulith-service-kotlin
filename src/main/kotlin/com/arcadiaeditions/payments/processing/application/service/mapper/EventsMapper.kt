package com.arcadiaeditions.payments.processing.application.service.mapper

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.application.port.driving.dto.*
import com.arcadiaeditions.payments.processing.domain.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    @Mapping(target = "reason", source = "failureReason")
    fun asPaymentDeclined(payment: Payment): com.arcadiaeditions.payments.processing.domain.event.PaymentDeclined

    @Mapping(target = "reason", source = "failureReason")
    @Mapping(target = "exhaustedAt", ignore = true)
    fun asPaymentRetryExhausted(
        payment: Payment
    ): com.arcadiaeditions.payments.processing.domain.event.PaymentRetryExhausted

    fun asPaymentCaptured(payment: Payment): com.arcadiaeditions.payments.processing.domain.event.PaymentCaptured

    fun asPaymentAuthorized(payment: Payment): com.arcadiaeditions.payments.processing.domain.event.PaymentAuthorized

    @Mapping(target = "retriedAt", ignore = true)
    fun asPaymentRetried(payment: Payment): com.arcadiaeditions.payments.processing.domain.event.PaymentRetried

    fun asPaymentCaptureFailed(
        payment: Payment
    ): com.arcadiaeditions.payments.processing.domain.event.PaymentCaptureFailed

    fun asPaymentVoided(payment: Payment): com.arcadiaeditions.payments.processing.domain.event.PaymentVoided

    @Mapping(target = "reason", source = "failureReason")
    fun asPaymentFailed(payment: Payment): com.arcadiaeditions.payments.processing.domain.event.PaymentFailed
}
