package com.arcadiaeditions.fulfillment.shipping.service.dtos

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ShippingAddressInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ShippingAddressInput(
    @NotNull val name: String,
    @NotNull val line1: String,
    val line2: String?,
    @NotNull val city: String,
    val region: String?,
    @NotNull val postalCode: String,
    @NotNull val country: String,
) : Serializable {}
