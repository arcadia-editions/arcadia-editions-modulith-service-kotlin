package com.arcadiaeditions.catalog.products.service.dtos

import com.arcadiaeditions.catalog.products.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** CreateProductInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class CreateProductInput(
    @NotNull val sku: String,
    @NotNull val name: String,
    val description: String?,
    @NotNull val price: BigDecimal,
    @NotNull val currency: String,
    @NotNull val editionSize: Integer,
    @NotNull val trackingMode: InventoryTrackingMode,
    val launchAt: Instant?,
) : Serializable {}
