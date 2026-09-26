package com.arcadiaeditions.payments.processing.events.shipping

import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.shipping.mappers.FulfillmentShippingEventsListenerMapper
import com.arcadiaeditions.payments.processing.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for FulfillmentShippingZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class FulfillmentShippingEventsListener(
    private val paymentsProcessingService: PaymentsProcessingService,
    private val mapper: FulfillmentShippingEventsListenerMapper,
) {

    @ApplicationModuleListener
    fun onFulfillmentScheduled(event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled) {
        paymentsProcessingService.capturePayment(mapper.asCapturePaymentInput(event))
    }

    @ApplicationModuleListener
    fun onFulfillmentFailed(event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed) {
        paymentsProcessingService.voidPayment(mapper.asVoidPaymentInput(event))
    }
}
