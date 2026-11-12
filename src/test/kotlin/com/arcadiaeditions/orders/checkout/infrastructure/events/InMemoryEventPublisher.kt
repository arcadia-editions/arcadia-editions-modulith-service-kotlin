package com.arcadiaeditions.orders.checkout.infrastructure.events

import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.outbound.events.EventPublisher

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onOrderConfirmedItem(event: OrderConfirmedItem) {
        events.add(event)
    }

    override fun onOrderConfirmedShippingAddress(event: OrderConfirmedShippingAddress) {
        events.add(event)
    }

    override fun onStockUnavailableItem(event: StockUnavailableItem) {
        events.add(event)
    }

    override fun onStockUnavailable(event: StockUnavailable) {
        events.add(event)
    }

    override fun onOrderConfirmed(event: OrderConfirmed) {
        events.add(event)
    }

    override fun onOrderCancelled(event: OrderCancelled) {
        events.add(event)
    }

    override fun onOrderCreated(event: OrderCreated) {
        events.add(event)
    }
}
