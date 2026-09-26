package com.arcadiaeditions.orders.checkout.service

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Order]. */
@org.springframework.modulith.NamedInterface("OrdersCheckoutService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
interface OrdersCheckoutService {

    /** With Events: [[OrderCreated, StockUnavailable]]. */
    fun startOrderCheckout(input: StartOrderCheckoutInput): Order

    /** With Events: [OrderConfirmed]. */
    fun confirmOrder(input: ConfirmOrderInput): Order

    /** With Events: [OrderCancelled]. */
    fun cancelOrder(input: CancelOrderInput): Order
}
