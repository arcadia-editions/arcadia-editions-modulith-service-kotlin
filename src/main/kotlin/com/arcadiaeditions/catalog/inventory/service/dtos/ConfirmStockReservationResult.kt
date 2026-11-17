package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ConfirmStockReservationResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class ConfirmStockReservationResult(
    @NotNull val outcome: ConfirmStockReservationOutcome,
    val confirmed: StockReservationConfirmedResult?,
    val notHeld: StockReservationNotHeldResult?,
) : Serializable {}
