package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** CreateInventoryPositionInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class CreateInventoryPositionInput(@NotNull val sku: String, @NotNull val initialQuantity: Integer) :
    Serializable {}
