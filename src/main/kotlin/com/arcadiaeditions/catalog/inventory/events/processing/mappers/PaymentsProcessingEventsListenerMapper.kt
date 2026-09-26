package com.arcadiaeditions.catalog.inventory.events.processing.mappers

import com.arcadiaeditions.catalog.inventory.domain.events.*

/**
 * Regenerated mapping contract for PaymentsProcessingEventsListener. Customize the mappings in the generated-once
 * PaymentsProcessingEventsListenerMapStructMapper.
 */
interface PaymentsProcessingEventsListenerMapper {

    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.events.PaymentDeclined
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput

    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.events.PaymentRetryExhausted
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput

    fun asReleaseStockInput(
        event: com.arcadiaeditions.payments.processing.domain.events.PaymentVoided
    ): com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput
}
