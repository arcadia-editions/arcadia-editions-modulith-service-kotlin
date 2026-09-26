package com.arcadiaeditions.fulfillment.shipping.service

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Fulfillment]. */
@org.springframework.modulith.NamedInterface("FulfillmentShippingService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
interface FulfillmentShippingService {

    /** With Events: [[FulfillmentScheduled, FulfillmentFailed]]. */
    fun scheduleFulfillment(input: ScheduleFulfillmentInput): Fulfillment

    /** With Events: [FulfillmentReadyToShip]. */
    fun markReadyToShip(id: Long): Fulfillment
}
