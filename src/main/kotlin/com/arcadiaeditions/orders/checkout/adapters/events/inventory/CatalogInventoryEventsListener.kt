package com.arcadiaeditions.orders.checkout.adapters.events.inventory

import com.arcadiaeditions.orders.checkout.adapters.events.inventory.mappers.CatalogInventoryEventsListenerMapper
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for CatalogInventoryZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
class CatalogInventoryEventsListener(private val ordersCheckoutService: OrdersCheckoutService) {

    private val mapper: CatalogInventoryEventsListenerMapper = CatalogInventoryEventsListenerMapper.INSTANCE

    @ApplicationModuleListener
    fun onStockReleased(event: com.arcadiaeditions.catalog.inventory.domain.events.StockReleased) {
        ordersCheckoutService.cancelOrder(mapper.asCancelOrderInput(event))
    }
}
