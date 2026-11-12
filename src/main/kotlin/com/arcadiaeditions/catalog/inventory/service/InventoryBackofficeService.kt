package com.arcadiaeditions.catalog.inventory.service

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [InventoryPosition]. */
@org.springframework.modulith.NamedInterface("InventoryBackofficeService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
interface InventoryBackofficeService {

    /**  */
    fun createInventoryPosition(input: CreateInventoryPositionInput): InventoryPosition

    /** With Events: [StockReceived]. */
    fun receiveStock(input: ReceiveStockInput): InventoryPosition

    /** With Events: [StockAdjusted]. */
    fun adjustStock(input: AdjustStockInput): InventoryPosition
}
