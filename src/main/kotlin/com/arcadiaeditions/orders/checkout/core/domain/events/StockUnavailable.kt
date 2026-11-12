package com.arcadiaeditions.orders.checkout.core.domain.events

import com.arcadiaeditions.orders.checkout.core.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.StockUnavailable")
@org.jmolecules.event.annotation.DomainEvent
data class StockUnavailable(
    var orderId: String? = null,
    var customerId: String? = null,
    var customerEmail: String? = null,
    var unavailableItems: MutableList<StockUnavailableItem>? = mutableListOf(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    fun addUnavailableItems(unavailableItems: StockUnavailableItem): StockUnavailable {
        this.unavailableItems?.add(unavailableItems)
        return this
    }
}
