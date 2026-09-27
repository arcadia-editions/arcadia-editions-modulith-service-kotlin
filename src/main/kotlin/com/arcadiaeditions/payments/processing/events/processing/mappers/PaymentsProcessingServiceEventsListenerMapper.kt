package com.arcadiaeditions.payments.processing.events.processing.mappers

import com.arcadiaeditions.payments.processing.domain.events.*

/**
 * Regenerated mapping contract for PaymentsProcessingServiceEventsListener. Customize the mappings in the
 * generated-once PaymentsProcessingServiceEventsListenerMapStructMapper.
 */
interface PaymentsProcessingServiceEventsListenerMapper {

    fun asRetryPaymentInput(
        event: PaymentFailed
    ): com.arcadiaeditions.payments.processing.service.dtos.RetryPaymentInput

    fun asVoidPaymentInput(
        event: PaymentCaptureFailed
    ): com.arcadiaeditions.payments.processing.service.dtos.VoidPaymentInput
}
