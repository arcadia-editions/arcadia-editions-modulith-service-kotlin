package com.arcadiaeditions.orders.checkout.events

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onOrderCreated(event: OrderCreated) {
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
}
