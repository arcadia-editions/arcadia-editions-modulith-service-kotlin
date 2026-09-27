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

    /** With Events: [StockReleased]. */
    fun releaseStock(input: ReleaseStockInput): List<StockReservation>

    /** With Events: [StockReservationConfirmed]. */
    fun confirmStockReservation(input: ConfirmStockReservationInput): List<StockReservation>
}
