package com.arcadiaeditions.catalog.inventory.domain.events

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.StockReceived")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class StockReceived(
    var sku: String? = null,
    var quantity: Integer? = null,
    var availableQuantity: Integer? = null,
    var reservedQuantity: Integer? = null,
    var soldQuantity: Integer? = null,
    var reason: String? = null,
    var reference: String? = null,
    var receivedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
