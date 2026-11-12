package com.arcadiaeditions.orders.checkout.infrastructure.events

import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.outbound.events.EventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onOrderConfirmedItem(event: OrderConfirmedItem) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onOrderConfirmedShippingAddress(event: OrderConfirmedShippingAddress) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockUnavailableItem(event: StockUnavailableItem) {
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

    override fun onOrderCreated(event: OrderCreated) {
        applicationEventPublisher.publishEvent(event)
    }
}
