package com.arcadiaeditions.orders.checkout.events

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*

interface EventPublisher {

    fun onOrderConfirmedItem(event: OrderConfirmedItem)

    fun onOrderConfirmedShippingAddress(event: OrderConfirmedShippingAddress)

    fun onStockUnavailableItem(event: StockUnavailableItem)

    fun onOrderCreated(event: OrderCreated)

    fun onStockUnavailable(event: StockUnavailable)

    fun onOrderConfirmed(event: OrderConfirmed)

    fun onOrderCancelled(event: OrderCancelled)
}
