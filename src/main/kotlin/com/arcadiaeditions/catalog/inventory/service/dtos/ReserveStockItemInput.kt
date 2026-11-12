package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ReserveStockItemInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ReserveStockItemInput(@NotNull val sku: String, @NotNull @Min(1) val quantity: Integer) : Serializable {}
