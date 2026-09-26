package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** AdjustStockInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class AdjustStockInput(
    @NotNull val sku: String,
    @NotNull val quantityDelta: Integer,
    @NotNull val reason: StockAdjustmentReason,
    val reference: String?,
) : Serializable {}
