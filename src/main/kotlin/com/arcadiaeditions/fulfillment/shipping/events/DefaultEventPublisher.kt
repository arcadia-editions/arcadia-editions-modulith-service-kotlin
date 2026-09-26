package com.arcadiaeditions.fulfillment.shipping.events

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.layered.InfrastructureLayer
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onFulfillmentFailed(event: FulfillmentFailed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onFulfillmentReadyToShip(event: FulfillmentReadyToShip) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onFulfillmentScheduled(event: FulfillmentScheduled) {
        applicationEventPublisher.publishEvent(event)
    }
}
