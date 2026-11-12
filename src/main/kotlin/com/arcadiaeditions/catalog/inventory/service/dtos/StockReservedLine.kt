package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockReservedLine. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockReservedLine(
    val reservationId: String?,
    val sku: String?,
    val quantity: Integer?,
    val reservedAt: Instant?,
) : Serializable {}
