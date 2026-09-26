package com.arcadiaeditions.orders.checkout.events.inventory

import com.arcadiaeditions.orders.checkout.domain.events.*
import com.arcadiaeditions.orders.checkout.events.inventory.mappers.CatalogInventoryEventsListenerMapper
import com.arcadiaeditions.orders.checkout.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for CatalogInventoryZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class CatalogInventoryEventsListener(
    private val ordersCheckoutService: OrdersCheckoutService,
    private val mapper: CatalogInventoryEventsListenerMapper,
) {

    @ApplicationModuleListener
    fun onStockReleased(event: com.arcadiaeditions.catalog.inventory.domain.events.StockReleased) {
        ordersCheckoutService.cancelOrder(mapper.asCancelOrderInput(event))
    }
}
