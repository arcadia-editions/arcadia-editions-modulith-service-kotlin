package com.arcadiaeditions.payments.processing.service.dtos

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** VoidPaymentInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class VoidPaymentInput(@NotNull val paymentId: String, @NotNull val orderId: String, @NotNull val reason: String) :
    Serializable {}
