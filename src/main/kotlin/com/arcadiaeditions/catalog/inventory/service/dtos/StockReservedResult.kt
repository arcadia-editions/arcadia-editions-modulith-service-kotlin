package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockReservedResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockReservedResult(
    val reservationId: String?,
    val orderId: String?,
    val sku: String?,
    val quantity: Integer?,
    val reservedAt: Instant?,
    val expiresAt: Instant?,
) : Serializable {}
