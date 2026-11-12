package com.arcadiaeditions.catalog.inventory.events.processing.mappers

import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentDeclined
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentRetryExhausted
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.event.PaymentVoided
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput
}
