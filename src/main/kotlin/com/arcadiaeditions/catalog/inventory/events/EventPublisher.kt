package com.arcadiaeditions.catalog.inventory.events

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*

interface EventPublisher {

    fun onStockReservationConfirmed(event: StockReservationConfirmed)

    fun onStockAdjusted(event: StockAdjusted)

    fun onStockReceived(event: StockReceived)

    fun onStockReleased(event: StockReleased)
}
