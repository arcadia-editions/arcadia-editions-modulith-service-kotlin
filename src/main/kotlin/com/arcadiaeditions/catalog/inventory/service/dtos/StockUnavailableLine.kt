package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockUnavailableLine. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockUnavailableLine(val sku: String?, val requestedQuantity: Integer?, val availableQuantity: Integer?) :
    Serializable {}
