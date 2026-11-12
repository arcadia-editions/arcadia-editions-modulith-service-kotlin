package com.arcadiaeditions.catalog.inventory.domain.events

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.StockReleased")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class StockReleased(
    var orderId: String? = null,
    var reason: StockReleaseReason? = null,
    var releasedAt: Instant? = null,
    var items: MutableList<StockReleasedItem>? = mutableListOf(),
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }

    fun addItems(items: StockReleasedItem): StockReleased {
        this.items?.add(items)
        return this
    }
}
