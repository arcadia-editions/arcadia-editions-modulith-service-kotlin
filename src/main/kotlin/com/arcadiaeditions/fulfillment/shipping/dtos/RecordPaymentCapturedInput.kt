package com.arcadiaeditions.fulfillment.shipping.dtos

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** RecordPaymentCapturedInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class RecordPaymentCapturedInput(@NotNull val orderId: String, @NotNull val capturedAt: Instant) : Serializable {}
