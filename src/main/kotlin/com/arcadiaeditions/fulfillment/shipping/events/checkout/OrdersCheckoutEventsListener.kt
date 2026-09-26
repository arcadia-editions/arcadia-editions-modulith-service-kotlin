package com.arcadiaeditions.fulfillment.shipping.events.checkout

import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.events.checkout.mappers.OrdersCheckoutEventsListenerMapper
import com.arcadiaeditions.fulfillment.shipping.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for OrdersCheckoutZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class OrdersCheckoutEventsListener(
    private val fulfillmentShippingService: FulfillmentShippingService,
    private val mapper: OrdersCheckoutEventsListenerMapper,
) {

    @ApplicationModuleListener
    fun onOrderConfirmed(event: com.arcadiaeditions.orders.checkout.domain.events.OrderConfirmed) {
        fulfillmentShippingService.scheduleFulfillment(mapper.asScheduleFulfillmentInput(event))
    }
}
