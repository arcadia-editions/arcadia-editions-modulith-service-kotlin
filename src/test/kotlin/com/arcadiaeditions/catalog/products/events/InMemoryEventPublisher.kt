package com.arcadiaeditions.catalog.products.events

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.*

class InMemoryEventPublisher : EventPublisher {

    private val events = mutableListOf<Any>()

    fun getEvents(): MutableList<Any> {
        return events
    }

    override fun onProductPublished(event: ProductPublished) {
        events.add(event)
    }

    override fun onProductRetired(event: ProductRetired) {
        events.add(event)
    }
}
