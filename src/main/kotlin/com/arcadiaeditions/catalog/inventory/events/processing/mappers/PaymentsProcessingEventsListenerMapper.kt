package com.arcadiaeditions.catalog.inventory.events.processing.mappers

import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    /** The event type fixes the release reason; the event's own `reason` is the payment failure text. */
    @Mapping(target = "reason", constant = "PAYMENT_DECLINED")
    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentDeclined
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput

    /** The event type fixes the release reason; the event's own `reason` is the payment failure text. */
    @Mapping(target = "reason", constant = "PAYMENT_RETRY_EXHAUSTED")
    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentRetryExhausted
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput

    /** The event type fixes the release reason; the event's own `reason` is the payment failure text. */
    @Mapping(target = "reason", constant = "PAYMENT_VOIDED")
    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentVoided
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput
}
