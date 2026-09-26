package com.arcadiaeditions.catalog.inventory.service.impl.mappers

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface InventoryServiceMapper {

    companion object {
        val INSTANCE: InventoryServiceMapper = Mappers.getMapper(InventoryServiceMapper::class.java)
    }

    // input mappings
    // ReserveStockInputnull-StockReservation confirmStockReservation

    fun asStockReservation(input: ReserveStockInput): StockReservation

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: StockReservation, input: ReserveStockInput): StockReservation

    // ReleaseStockInputnull-StockReservation releaseStock

    fun asStockReservation(input: ReleaseStockInput): StockReservation

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: StockReservation, input: ReleaseStockInput): StockReservation

    // output mappings
    // StockReservation-StockReservedResult reserveStock
    fun asStockReservedResult(entity: StockReservation): StockReservedResult
}
