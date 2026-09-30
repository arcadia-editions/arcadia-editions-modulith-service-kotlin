package com.arcadiaeditions.orders.checkout.events.inventory.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.orders.checkout.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for CatalogInventoryEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 */
@Mapper(uses = [BaseMapper::class])
interface CatalogInventoryEventsListenerMapper {

    companion object {
        val INSTANCE: CatalogInventoryEventsListenerMapper = Mappers.getMapper(CatalogInventoryEventsListenerMapper::class.java)
    }

    fun asCancelOrderInput(
        event: com.arcadiaeditions.catalog.inventory.domain.events.StockReleased
    ): com.arcadiaeditions.orders.checkout.service.dtos.CancelOrderInput
}
