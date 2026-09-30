package com.arcadiaeditions.catalog.inventory.service.impl.mappers

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import java.time.Instant
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface InventoryServiceMapper {

    companion object {
        val INSTANCE: InventoryServiceMapper = Mappers.getMapper(InventoryServiceMapper::class.java)
    }

    // output mappings
    // G-CI-01: reservationId/sku/quantity/reservedAt all match by name -> auto-mapped, no warnings.
    fun asStockReservedLine(stockReservation: StockReservation): StockReservedLine

    // reserveStock has no single entity as its source (it reserves N lines for an order), so the
    // RESERVED outcome is composed manually from the order id/expiry plus the saved reservations
    // (skill: "add an overload that takes the extra source").
    fun asStockReservedResult(
        orderId: String?,
        expiresAt: Instant?,
        reservations: List<StockReservation>,
    ): StockReservedResult {
        val result = StockReservedResult(orderId = orderId, expiresAt = expiresAt)
        reservations.forEach { result.addReservations(asStockReservedLine(it)) }
        return result
    }

    fun asReserveStockResult(orderId: String?, expiresAt: Instant?, reservations: List<StockReservation>): ReserveStockResult =
        ReserveStockResult(
            outcome = ReserveStockOutcome.RESERVED,
            reserved = asStockReservedResult(orderId, expiresAt, reservations),
            unavailable = null,
        )
}
