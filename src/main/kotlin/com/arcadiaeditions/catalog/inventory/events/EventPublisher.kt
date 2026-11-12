package com.arcadiaeditions.catalog.inventory.events

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*

interface EventPublisher {

    fun onStockAdjusted(event: StockAdjusted)

    fun onStockReservationConfirmed(event: StockReservationConfirmed)

    fun onStockReleased(event: StockReleased)

    fun onStockReservationConfirmedItem(event: StockReservationConfirmedItem)

    fun onStockReceived(event: StockReceived)

    fun onStockReleasedItem(event: StockReleasedItem)
}
