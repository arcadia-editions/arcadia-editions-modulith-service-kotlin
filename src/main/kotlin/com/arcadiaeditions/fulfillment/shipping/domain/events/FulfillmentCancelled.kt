package com.arcadiaeditions.fulfillment.shipping.domain.events

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.FulfillmentCancelled")
@org.jmolecules.event.annotation.DomainEvent
data class FulfillmentCancelled(
    var fulfillmentId: String? = null,
    var orderId: String? = null,
    var customerId: String? = null,
    var reason: String? = null,
    var cancelledAt: Instant? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
