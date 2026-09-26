package com.arcadiaeditions.notifications.consumer.service.dtos

import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** SendOrderCancelledNotificationInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class SendOrderCancelledNotificationInput(
    @NotNull val orderId: String,
    @NotNull val customerId: String,
    @NotNull val reason: String,
    @NotNull val email: String,
) : Serializable {}
