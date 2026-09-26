package com.arcadiaeditions.orders.checkout.domain.events

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.OrderCancelled")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class OrderCancelled(var orderId: String? = null, var version: Integer? = null, var cancelledAt: Instant? = null) :
    Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
