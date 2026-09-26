package com.arcadiaeditions.catalog.inventory.service.impl.mappers

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface InventoryBackofficeServiceMapper {

    companion object {
        val INSTANCE: InventoryBackofficeServiceMapper = Mappers.getMapper(InventoryBackofficeServiceMapper::class.java)
    }

    // input mappings
    // CreateInventoryPositionInputnull-InventoryPosition createInventoryPosition

    fun asInventoryPosition(input: CreateInventoryPositionInput): InventoryPosition

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: InventoryPosition, input: CreateInventoryPositionInput): InventoryPosition

    // ReceiveStockInputnull-InventoryPosition receiveStock

    fun asInventoryPosition(input: ReceiveStockInput): InventoryPosition

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: InventoryPosition, input: ReceiveStockInput): InventoryPosition

    // AdjustStockInputnull-InventoryPosition adjustStock

    fun asInventoryPosition(input: AdjustStockInput): InventoryPosition

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: InventoryPosition, input: AdjustStockInput): InventoryPosition
    // output mappings
}
