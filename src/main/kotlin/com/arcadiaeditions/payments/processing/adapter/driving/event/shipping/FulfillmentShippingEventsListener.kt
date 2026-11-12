package com.arcadiaeditions.payments.processing.adapter.driving.event.shipping

import com.arcadiaeditions.payments.processing.adapter.driving.event.shipping.mapper.FulfillmentShippingEventsListenerMapper
import com.arcadiaeditions.payments.processing.application.port.driving.*
import com.arcadiaeditions.payments.processing.domain.event.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for FulfillmentShippingZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
class FulfillmentShippingEventsListener(private val paymentsProcessingService: PaymentsProcessingService) {

    private val mapper: FulfillmentShippingEventsListenerMapper = FulfillmentShippingEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onFulfillmentScheduled(event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentScheduled) {
        paymentsProcessingService.capturePayment(mapper.asCapturePaymentInput(event))
    }

    @ApplicationModuleListener
    fun onFulfillmentFailed(event: com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed) {
        paymentsProcessingService.voidPayment(mapper.asVoidPaymentInput(event))
    }
}
