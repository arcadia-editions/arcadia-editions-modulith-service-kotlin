package com.arcadiaeditions.fulfillment.shipping.dtos

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** CancelFulfillmentInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class CancelFulfillmentInput(@NotNull val orderId: String, @NotNull val reason: String) : Serializable {}
