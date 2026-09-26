package com.arcadiaeditions.fulfillment.shipping.events

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*

interface EventPublisher {

    fun onFulfillmentFailed(event: FulfillmentFailed)

    fun onFulfillmentReadyToShip(event: FulfillmentReadyToShip)

    fun onFulfillmentScheduled(event: FulfillmentScheduled)
}
