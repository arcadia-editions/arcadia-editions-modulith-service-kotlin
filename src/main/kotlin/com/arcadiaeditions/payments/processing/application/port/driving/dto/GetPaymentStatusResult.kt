package com.arcadiaeditions.payments.processing.application.port.driving.dto

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** GetPaymentStatusResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class GetPaymentStatusResult(
    @NotNull val outcome: GetPaymentStatusOutcome,
    val noPayment: NoPaymentResult?,
    val started: PaymentStartedResult?,
) : Serializable {}
