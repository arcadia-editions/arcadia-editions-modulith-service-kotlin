package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockReservationNotHeldResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockReservationNotHeldResult(val orderId: String?) : Serializable {}
