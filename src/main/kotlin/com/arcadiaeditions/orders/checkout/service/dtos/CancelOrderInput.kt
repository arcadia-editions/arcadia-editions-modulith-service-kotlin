package com.arcadiaeditions.orders.checkout.service.dtos

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** CancelOrderInput. */
@org.jmolecules.ddd.annotation.ValueObject data class CancelOrderInput(@NotNull val orderId: String) : Serializable {}
