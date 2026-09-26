package com.arcadiaeditions.orders.checkout.events

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.layered.InfrastructureLayer
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onOrderCreated(event: OrderCreated) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockUnavailable(event: StockUnavailable) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onOrderConfirmed(event: OrderConfirmed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onOrderCancelled(event: OrderCancelled) {
        applicationEventPublisher.publishEvent(event)
    }
}
