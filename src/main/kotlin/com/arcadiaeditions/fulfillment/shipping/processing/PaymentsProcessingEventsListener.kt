package com.arcadiaeditions.fulfillment.shipping.processing

import com.arcadiaeditions.fulfillment.shipping.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.processing.mappers.PaymentsProcessingEventsListenerMapper
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for PaymentsProcessingZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
class PaymentsProcessingEventsListener(private val fulfillmentShippingService: FulfillmentShippingService) {

    private val mapper: PaymentsProcessingEventsListenerMapper = PaymentsProcessingEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onPaymentCaptured(event: com.arcadiaeditions.payments.processing.domain.event.PaymentCaptured) {
        fulfillmentShippingService.recordPaymentCaptured(mapper.asRecordPaymentCapturedInput(event))
    }
}
