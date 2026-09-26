package com.arcadiaeditions.orders.checkout.events.inventory.mappers

import com.arcadiaeditions.orders.checkout.domain.events.*

/**
 * Regenerated mapping contract for CatalogInventoryEventsListener. Customize the mappings in the generated-once
 * CatalogInventoryEventsListenerMapStructMapper.
 */
interface CatalogInventoryEventsListenerMapper {

    fun asCancelOrderInput(
        event: com.arcadiaeditions.catalog.inventory.domain.events.StockReleased
    ): com.arcadiaeditions.orders.checkout.service.dtos.CancelOrderInput
}
