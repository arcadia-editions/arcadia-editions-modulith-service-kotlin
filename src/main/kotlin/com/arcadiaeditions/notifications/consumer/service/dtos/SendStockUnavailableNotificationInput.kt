package com.arcadiaeditions.notifications.consumer.service.dtos

import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** SendStockUnavailableNotificationInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class SendStockUnavailableNotificationInput(
    @NotNull val customerId: String,
    @NotNull val sku: String,
    @NotNull val email: String,
) : Serializable {}
