package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockReservedResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockReservedResult(
    val orderId: String?,
    val expiresAt: Instant?,
    val reservations: MutableList<StockReservedLine> = mutableListOf(),
) : Serializable {

    fun addReservations(reservations: StockReservedLine): StockReservedResult {
        this.reservations += reservations
        return this
    }
}
