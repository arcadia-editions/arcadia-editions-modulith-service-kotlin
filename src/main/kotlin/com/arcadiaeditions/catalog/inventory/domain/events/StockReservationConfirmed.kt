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
    var reservationId: String? = null,
    var orderId: String? = null,
    var sku: String? = null,
    var quantity: Integer? = null,
    var reason: StockReleaseReason? = null,
    var releasedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
