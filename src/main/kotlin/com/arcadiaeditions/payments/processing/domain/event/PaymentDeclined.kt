package com.arcadiaeditions.payments.processing.domain.event

import com.arcadiaeditions.payments.processing.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.PaymentDeclined")
@org.jmolecules.event.annotation.DomainEvent
data class PaymentDeclined(
    var orderId: String? = null,
    var customerId: String? = null,
    var amount: BigDecimal? = null,
    var currency: String? = null,
    var reason: String? = null,
    var declinedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
