package com.arcadiaeditions.orders.checkout.adapters.events.inventory.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface CatalogInventoryEventsListenerMapper {

    companion object {
        val INSTANCE: CatalogInventoryEventsListenerMapper =
            Mappers.getMapper(CatalogInventoryEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asCancelOrderInput(
        event: com.arcadiaeditions.catalog.inventory.domain.events.StockReleased
    ): com.arcadiaeditions.orders.checkout.core.inbound.dtos.CancelOrderInput
}
