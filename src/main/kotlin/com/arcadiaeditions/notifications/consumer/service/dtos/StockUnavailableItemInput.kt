package com.arcadiaeditions.notifications.consumer.service.dtos

import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockUnavailableItemInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockUnavailableItemInput(
    @NotNull val sku: String,
    val requestedQuantity: Integer?,
    val availableQuantity: Integer?,
) : Serializable {}
