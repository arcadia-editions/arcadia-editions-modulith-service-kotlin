package com.arcadiaeditions.fulfillment.shipping.service.dtos

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** FulfillmentItemInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class FulfillmentItemInput(@NotNull val sku: String, @NotNull val quantity: Integer) : Serializable {}
