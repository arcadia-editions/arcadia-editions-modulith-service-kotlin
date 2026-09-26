package com.arcadiaeditions.payments.processing.service.dtos

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** RetryPaymentInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class RetryPaymentInput(@NotNull val paymentId: String, @NotNull val orderId: String) : Serializable {}
