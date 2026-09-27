package com.arcadiaeditions.catalog.inventory.events

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onStockAdjusted(event: StockAdjusted) {
        events.add(event)
    }

    override fun onStockReservationConfirmed(event: StockReservationConfirmed) {
        events.add(event)
    }

    override fun onStockReleased(event: StockReleased) {
        events.add(event)
    }

    override fun onStockReservationConfirmedItem(event: StockReservationConfirmedItem) {
        events.add(event)
    }

    override fun onStockReceived(event: StockReceived) {
        events.add(event)
    }

    override fun onStockReleasedItem(event: StockReleasedItem) {
        events.add(event)
    }
}
