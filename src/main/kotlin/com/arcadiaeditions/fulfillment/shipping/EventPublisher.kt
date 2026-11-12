package com.arcadiaeditions.fulfillment.shipping

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*

interface EventPublisher {

    fun onFulfillmentScheduled(event: FulfillmentScheduled)

    fun onFulfillmentReadyToShip(event: FulfillmentReadyToShip)

    fun onFulfillmentFailed(event: FulfillmentFailed)
}
