package com.arcadiaeditions.orders.checkout.service.dtos

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ConfirmOrderInput. */
@org.jmolecules.ddd.annotation.ValueObject data class ConfirmOrderInput(@NotNull val orderId: String) : Serializable {}
