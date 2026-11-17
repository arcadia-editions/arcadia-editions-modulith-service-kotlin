package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockReservationConfirmedResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockReservationConfirmedResult(val orderId: String?, val confirmedAt: Instant?) : Serializable {}
