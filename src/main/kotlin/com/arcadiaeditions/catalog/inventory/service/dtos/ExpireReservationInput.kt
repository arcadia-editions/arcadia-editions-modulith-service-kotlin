package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ExpireReservationInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ExpireReservationInput(@NotNull val orderId: String) : Serializable {}
