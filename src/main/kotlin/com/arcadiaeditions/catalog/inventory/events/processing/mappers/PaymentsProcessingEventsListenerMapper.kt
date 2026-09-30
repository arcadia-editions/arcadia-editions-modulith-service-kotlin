package com.arcadiaeditions.catalog.inventory.events.processing.mappers

import com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput
import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.events.PaymentDeclined
import com.arcadiaeditions.payments.processing.domain.events.PaymentRetryExhausted
import com.arcadiaeditions.payments.processing.domain.events.PaymentVoided
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for PaymentsProcessingEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 *
 * G-CI-04: `reason` is fixed by the source event type, never taken as free text from the event
 * payload (that would allow `Enum.valueOf` to throw `IllegalArgumentException` on an unexpected
 * string). `ReleaseStockInput` only needs `orderId` + `reason`; `releaseStock` looks up the
 * reservations' sku/quantity itself.
 */
@Mapper(uses = [BaseMapper::class])
interface PaymentsProcessingEventsListenerMapper {

    companion object {
        val INSTANCE: PaymentsProcessingEventsListenerMapper =
            Mappers.getMapper(PaymentsProcessingEventsListenerMapper::class.java)
    }

    @Mapping(target = "reason", constant = "PAYMENT_DECLINED")
    fun asReleaseStockInput(event: PaymentDeclined): ReleaseStockInput

    @Mapping(target = "reason", constant = "PAYMENT_RETRY_EXHAUSTED")
    fun asReleaseStockInput(event: PaymentRetryExhausted): ReleaseStockInput

    @Mapping(target = "reason", constant = "PAYMENT_VOIDED")
    fun asReleaseStockInput(event: PaymentVoided): ReleaseStockInput
}
