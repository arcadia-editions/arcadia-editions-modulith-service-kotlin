package com.arcadiaeditions.orders.checkout.core.inbound

import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Order]. */
@org.springframework.modulith.NamedInterface("OrdersCheckoutService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.PrimaryPort
interface OrdersCheckoutService {

    /**
     * Persists the Order only on OrderCreated; StockUnavailable answers 409 and persists nothing (P-03 A).
     *
     * With Events: [[OrderCreated, StockUnavailable]].
     */
    fun startOrderCheckout(input: StartOrderCheckoutInput): Order

    /**
     * No-op on CONFIRMED (redelivery). On CANCELLED, or when the stock is no longer held (NOT_HELD), the Order is not
     * confirmed and OrderConfirmationRejected asks payments to void.
     *
     * With Events: [[OrderConfirmed, OrderConfirmationRejected]].
     */
    fun confirmOrder(input: ConfirmOrderInput): Order

    /**
     * No-op, no event, on CANCELLED (redelivered StockReleased).
     *
     * With Events: [OrderCancelled].
     */
    fun cancelOrder(input: CancelOrderInput): Order

    /**  */
    fun getOrder(id: Long): Order?
}
