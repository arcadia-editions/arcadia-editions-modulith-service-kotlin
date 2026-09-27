package com.arcadiaeditions.orders.checkout.service.dtos

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** OrderItemInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class OrderItemInput(
    @NotNull val sku: String,
    @NotNull @Min(1) val quantity: Integer,
    @NotNull val unitPrice: BigDecimal,
) : Serializable {}
