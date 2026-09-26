package com.arcadiaeditions.fulfillment.shipping.domain.events

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.FulfillmentScheduled")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class FulfillmentScheduled(
    var fulfillmentId: String? = null,
    var orderId: String? = null,
    var customerId: String? = null,
    var scheduledAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
