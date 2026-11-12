package com.arcadiaeditions.orders.checkout.core.outbound.events

import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.domain.events.*

@org.jmolecules.architecture.hexagonal.SecondaryPort
interface EventPublisher {

    fun onOrderConfirmedItem(event: OrderConfirmedItem)

    fun onOrderConfirmedShippingAddress(event: OrderConfirmedShippingAddress)

    fun onStockUnavailableItem(event: StockUnavailableItem)

    fun onStockUnavailable(event: StockUnavailable)

    fun onOrderConfirmed(event: OrderConfirmed)

    fun onOrderCancelled(event: OrderCancelled)

    fun onOrderCreated(event: OrderCreated)
}
