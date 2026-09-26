package com.arcadiaeditions.payments.processing.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asPaymentDeclined(payment: Payment): com.arcadiaeditions.payments.processing.domain.events.PaymentDeclined

    fun asPaymentRetryExhausted(
        payment: Payment
    ): com.arcadiaeditions.payments.processing.domain.events.PaymentRetryExhausted

    fun asPaymentCaptured(payment: Payment): com.arcadiaeditions.payments.processing.domain.events.PaymentCaptured

    fun asPaymentAuthorized(payment: Payment): com.arcadiaeditions.payments.processing.domain.events.PaymentAuthorized

    fun asPaymentRetried(payment: Payment): com.arcadiaeditions.payments.processing.domain.events.PaymentRetried

    fun asPaymentCaptureFailed(
        payment: Payment
    ): com.arcadiaeditions.payments.processing.domain.events.PaymentCaptureFailed

    fun asPaymentVoided(payment: Payment): com.arcadiaeditions.payments.processing.domain.events.PaymentVoided

    fun asPaymentFailed(payment: Payment): com.arcadiaeditions.payments.processing.domain.events.PaymentFailed
}
