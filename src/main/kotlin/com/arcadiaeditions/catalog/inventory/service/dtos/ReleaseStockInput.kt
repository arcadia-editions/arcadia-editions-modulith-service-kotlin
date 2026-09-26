package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ReleaseStockInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ReleaseStockInput(
    val reservationId: String?,
    @NotNull val orderId: String,
    @NotNull val sku: String,
    @NotNull val quantity: Integer,
    @NotNull val reason: StockReleaseReason,
) : Serializable {}
