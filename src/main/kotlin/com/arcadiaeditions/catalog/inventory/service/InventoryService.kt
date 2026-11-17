package com.arcadiaeditions.catalog.inventory.service

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [StockReservation]. */
@org.springframework.modulith.NamedInterface("InventoryService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
interface InventoryService {

    /**  */
    fun reserveStock(input: ReserveStockInput): ReserveStockResult

    /**
     * The type of the triggering event sets the reason. No-op, no event, when already RELEASED.
     *
     * With Events: [StockReleased].
     */
    fun releaseStock(input: ReleaseStockInput): List<StockReservation>

    /**
     * Reservation timer, 10 minutes after OrderCreated. No-op, no event, when no line of the order is RESERVED.
     * Releases them (RESERVATION_EXPIRED) only when payments answers NO_PAYMENT; on STARTED, payment owns the end and
     * nothing happens.
     *
     * With Events: [StockReleased].
     */
    fun expireReservation(input: ExpireReservationInput): List<StockReservation>

    /** With Events: [StockReservationConfirmed]. */
    fun confirmStockReservation(input: ConfirmStockReservationInput): ConfirmStockReservationResult
}
