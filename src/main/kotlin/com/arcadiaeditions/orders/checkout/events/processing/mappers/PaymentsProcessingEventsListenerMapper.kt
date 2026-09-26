package com.arcadiaeditions.orders.checkout.events.processing.mappers

import com.arcadiaeditions.orders.checkout.domain.events.*

/**
 * Regenerated mapping contract for PaymentsProcessingEventsListener. Customize the mappings in the generated-once
 * PaymentsProcessingEventsListenerMapStructMapper.
 */
interface PaymentsProcessingEventsListenerMapper {

    fun asConfirmOrderInput(
        event: com.arcadiaeditions.payments.processing.domain.events.PaymentAuthorized
    ): com.arcadiaeditions.orders.checkout.service.dtos.ConfirmOrderInput
}
