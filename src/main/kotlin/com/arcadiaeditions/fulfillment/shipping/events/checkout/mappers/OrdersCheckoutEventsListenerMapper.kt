package com.arcadiaeditions.fulfillment.shipping.events.checkout.mappers

import com.arcadiaeditions.fulfillment.shipping.domain.events.*

/**
 * Regenerated mapping contract for OrdersCheckoutEventsListener. Customize the mappings in the generated-once
 * OrdersCheckoutEventsListenerMapStructMapper.
 */
interface OrdersCheckoutEventsListenerMapper {

    fun asScheduleFulfillmentInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.OrderConfirmed
    ): com.arcadiaeditions.fulfillment.shipping.service.dtos.ScheduleFulfillmentInput
}
