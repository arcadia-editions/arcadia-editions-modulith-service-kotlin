package com.arcadiaeditions.fulfillment.shipping.checkout

import com.arcadiaeditions.fulfillment.shipping.*
import com.arcadiaeditions.fulfillment.shipping.checkout.mappers.OrdersCheckoutEventsListenerMapper
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for OrdersCheckoutZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
class OrdersCheckoutEventsListener(private val fulfillmentShippingService: FulfillmentShippingService) {

    private val mapper: OrdersCheckoutEventsListenerMapper = OrdersCheckoutEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onOrderConfirmed(event: com.arcadiaeditions.orders.checkout.core.domain.events.OrderConfirmed) {
        fulfillmentShippingService.scheduleFulfillment(mapper.asScheduleFulfillmentInput(event))
    }
}
