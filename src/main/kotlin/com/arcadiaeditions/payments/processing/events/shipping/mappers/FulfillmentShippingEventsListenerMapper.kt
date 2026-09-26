package com.arcadiaeditions.payments.processing.events.shipping.mappers

import com.arcadiaeditions.payments.processing.domain.events.*

/**
 * Regenerated mapping contract for FulfillmentShippingEventsListener. Customize the mappings in the generated-once
 * FulfillmentShippingEventsListenerMapStructMapper.
 */
interface FulfillmentShippingEventsListenerMapper {

    fun asCapturePaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled
    ): com.arcadiaeditions.payments.processing.service.dtos.CapturePaymentInput

    fun asVoidPaymentInput(
        event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed
    ): com.arcadiaeditions.payments.processing.service.dtos.VoidPaymentInput
}
