package com.arcadiaeditions.payments.processing.service.dtos

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** AuthorizePaymentInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class AuthorizePaymentInput(
    @NotNull val orderId: String,
    @NotNull val customerId: String,
    @NotNull val amount: BigDecimal,
    @NotNull val currency: String,
    @NotNull val paymentMethodId: String,
) : Serializable {}
