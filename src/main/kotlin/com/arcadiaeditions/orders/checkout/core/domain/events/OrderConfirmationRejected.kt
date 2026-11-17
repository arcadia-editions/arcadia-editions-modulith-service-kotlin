package com.arcadiaeditions.orders.checkout.core.domain.events

import com.arcadiaeditions.orders.checkout.core.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.OrderConfirmationRejected")
@org.jmolecules.event.annotation.DomainEvent
data class OrderConfirmationRejected(
    var orderId: String? = null,
    var customerId: String? = null,
    var reason: String? = null,
    var rejectedAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
