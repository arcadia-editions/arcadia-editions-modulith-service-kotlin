package com.arcadiaeditions.fulfillment.shipping.events

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onFulfillmentFailed(event: FulfillmentFailed) {
        events.add(event)
    }

    override fun onFulfillmentReadyToShip(event: FulfillmentReadyToShip) {
        events.add(event)
    }

    override fun onFulfillmentScheduled(event: FulfillmentScheduled) {
        events.add(event)
    }
}
