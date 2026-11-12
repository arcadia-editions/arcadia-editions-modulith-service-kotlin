package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ReceiveStockInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ReceiveStockInput(
    @NotNull val sku: String,
    @NotNull val quantity: Integer,
    val reason: String?,
    val reference: String?,
) : Serializable {}
