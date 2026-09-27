package com.arcadiaeditions.catalog.inventory.domain.events

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.StockReservationConfirmed")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class StockReservationConfirmed(
    var orderId: String? = null,
    var confirmedAt: Instant? = null,
    var items: MutableList<StockReservationConfirmedItem>? = mutableListOf(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    fun addItems(items: StockReservationConfirmedItem): StockReservationConfirmed {
        this.items?.add(items)
        return this
    }
}
