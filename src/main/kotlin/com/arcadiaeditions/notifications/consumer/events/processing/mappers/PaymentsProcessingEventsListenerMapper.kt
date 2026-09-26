package com.arcadiaeditions.notifications.consumer.events.processing.mappers

import com.arcadiaeditions.notifications.consumer.domain.events.*

/**
 * Regenerated mapping contract for PaymentsProcessingEventsListener. Customize the mappings in the generated-once
 * PaymentsProcessingEventsListenerMapStructMapper.
 */
interface PaymentsProcessingEventsListenerMapper {

    fun asSendOrderConfirmationInput(
        event: com.arcadiaeditions.payments.processing.domain.events.PaymentCaptured
    ): com.arcadiaeditions.notifications.consumer.service.dtos.SendOrderConfirmationInput
}
