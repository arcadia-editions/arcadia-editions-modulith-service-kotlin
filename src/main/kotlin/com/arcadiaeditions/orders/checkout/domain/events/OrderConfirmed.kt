package com.arcadiaeditions.orders.checkout.domain.events

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.OrderConfirmed")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class OrderConfirmed(
    var orderId: String? = null,
    var version: Integer? = null,
    var customerId: String? = null,
    var confirmedAt: Instant? = null,
    var items: MutableList<OrderConfirmedItem>? = mutableListOf(),
    var shippingAddress: OrderConfirmedShippingAddress? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    fun addItems(items: OrderConfirmedItem): OrderConfirmed {
        this.items?.add(items)
        return this
    }
}
