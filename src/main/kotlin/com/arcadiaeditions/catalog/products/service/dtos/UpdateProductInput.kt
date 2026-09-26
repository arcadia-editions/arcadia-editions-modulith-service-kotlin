package com.arcadiaeditions.catalog.products.service.dtos

import com.arcadiaeditions.catalog.products.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** UpdateProductInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class UpdateProductInput(
    val name: String?,
    val description: String?,
    val price: BigDecimal?,
    val currency: String?,
    val launchAt: Instant?,
) : Serializable {}
