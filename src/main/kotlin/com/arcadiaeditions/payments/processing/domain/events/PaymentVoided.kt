package com.arcadiaeditions.payments.processing.domain.events

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.PaymentVoided")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class PaymentVoided(
    var paymentId: String? = null,
    var orderId: String? = null,
    var customerId: String? = null,
    var providerReference: String? = null,
    var reason: String? = null,
    var voidedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
