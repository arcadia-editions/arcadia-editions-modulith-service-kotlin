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

    /** With Events: [[OrderCreated, StockUnavailable]]. */
    fun startOrderCheckout(input: StartOrderCheckoutInput): Order

    /** With Events: [OrderConfirmed]. */
    fun confirmOrder(input: ConfirmOrderInput): Order

    /** With Events: [OrderCancelled]. */
    fun cancelOrder(input: CancelOrderInput): Order

    /**  */
    fun getOrder(id: Long): Order?
}
