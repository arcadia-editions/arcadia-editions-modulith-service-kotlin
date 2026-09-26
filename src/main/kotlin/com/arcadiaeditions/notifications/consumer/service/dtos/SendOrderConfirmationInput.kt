package com.arcadiaeditions.notifications.consumer.service.dtos

import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** SendOrderConfirmationInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class SendOrderConfirmationInput(
    @NotNull val orderId: String,
    @NotNull val customerId: String,
    @NotNull val email: String,
) : Serializable {}
