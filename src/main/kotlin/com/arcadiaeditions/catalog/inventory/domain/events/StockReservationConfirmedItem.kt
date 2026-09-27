package com.arcadiaeditions.catalog.inventory.domain.events

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.StockReservationConfirmedItem")
data class StockReservationConfirmedItem(
    var reservationId: String? = null,
    var sku: String? = null,
    var quantity: Integer? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
