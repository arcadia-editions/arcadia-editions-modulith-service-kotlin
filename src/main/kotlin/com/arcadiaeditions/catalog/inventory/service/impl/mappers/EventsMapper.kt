package com.arcadiaeditions.catalog.inventory.service.impl.mappers

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asStockAdjusted(
        inventoryPosition: InventoryPosition
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockAdjusted

    fun asStockReservationConfirmed(
        input: ReserveStockInput?
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReservationConfirmed

    fun asStockReleased(
        stockReservation: StockReservation
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReleased

    fun asStockReceived(
        inventoryPosition: InventoryPosition
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReceived
}
