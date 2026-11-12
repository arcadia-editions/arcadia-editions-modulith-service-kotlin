package com.arcadiaeditions.payments.processing.domain.event

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.PaymentCaptured")
@org.jmolecules.event.annotation.DomainEvent
data class PaymentCaptured(
    var orderId: String? = null,
    var customerId: String? = null,
    var customerEmail: String? = null,
    var amount: BigDecimal? = null,
    var currency: String? = null,
    var providerReference: String? = null,
    var capturedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
