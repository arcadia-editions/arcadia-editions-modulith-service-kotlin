package com.arcadiaeditions.payments.processing.application.port.driving.dto

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** NoPaymentResult. */
@org.jmolecules.ddd.annotation.ValueObject data class NoPaymentResult(val orderId: String?) : Serializable {}
