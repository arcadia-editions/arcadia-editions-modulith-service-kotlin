package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ReserveStockInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ReserveStockInput(@NotNull val orderId: String, @NotNull val sku: String, @NotNull val quantity: Integer) :
    Serializable {}
