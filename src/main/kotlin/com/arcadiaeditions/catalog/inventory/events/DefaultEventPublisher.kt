package com.arcadiaeditions.catalog.inventory.events

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
@org.jmolecules.architecture.layered.InfrastructureLayer
class DefaultEventPublisher(private val applicationEventPublisher: ApplicationEventPublisher) : EventPublisher {

    override fun onStockAdjusted(event: StockAdjusted) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockReservationConfirmed(event: StockReservationConfirmed) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockReleased(event: StockReleased) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockReservationConfirmedItem(event: StockReservationConfirmedItem) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockReceived(event: StockReceived) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun onStockReleasedItem(event: StockReleasedItem) {
        applicationEventPublisher.publishEvent(event)
    }
}
