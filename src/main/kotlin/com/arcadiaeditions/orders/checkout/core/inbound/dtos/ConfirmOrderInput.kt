package com.arcadiaeditions.orders.checkout.core.inbound.dtos

import com.arcadiaeditions.orders.checkout.core.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ConfirmOrderInput. */
@org.jmolecules.ddd.annotation.ValueObject data class ConfirmOrderInput(@NotNull val orderId: String) : Serializable {}
