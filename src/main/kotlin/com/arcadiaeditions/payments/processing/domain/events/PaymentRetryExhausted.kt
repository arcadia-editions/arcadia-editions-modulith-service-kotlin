package com.arcadiaeditions.payments.processing.domain.events

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.PaymentRetryExhausted")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class PaymentRetryExhausted(
    var paymentId: String? = null,
    var orderId: String? = null,
    var customerId: String? = null,
    var attemptCount: Integer? = null,
    var reason: String? = null,
    var exhaustedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
