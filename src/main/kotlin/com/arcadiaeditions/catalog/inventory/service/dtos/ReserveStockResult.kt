package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ReserveStockResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class ReserveStockResult(
    @NotNull val outcome: ReserveStockOutcome,
    val reserved: StockReservedResult?,
    val unavailable: StockUnavailableResult?,
) : Serializable {}
