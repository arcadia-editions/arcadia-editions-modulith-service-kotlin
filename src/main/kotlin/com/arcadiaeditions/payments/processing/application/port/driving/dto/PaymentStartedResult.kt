package com.arcadiaeditions.payments.processing.application.port.driving.dto

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** PaymentStartedResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class PaymentStartedResult(val orderId: String?, val status: PaymentStatus?) : Serializable {}
