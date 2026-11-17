package com.arcadiaeditions.fulfillment.shipping

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Fulfillment]. */
@org.springframework.modulith.NamedInterface("FulfillmentShippingService")
@org.jspecify.annotations.NullMarked
interface FulfillmentShippingService {

    /**
     * No-op, no event, when a Fulfillment exists for orderId (redelivery).
     *
     * With Events: [[FulfillmentScheduled, FulfillmentFailed]].
     */
    fun scheduleFulfillment(input: ScheduleFulfillmentInput): Fulfillment

    /**
     * Money moved: the warehouse may now prepare the parcel. Sets paymentCapturedAt; no state change, no event. No-op
     * when there is no Fulfillment, it is not SCHEDULED, or paymentCapturedAt is set.
     */
    fun recordPaymentCaptured(input: RecordPaymentCapturedInput): Fulfillment?

    /**
     * Warehouse action. Refused (no transition, no event) until paymentCapturedAt is set.
     *
     * With Events: [FulfillmentReadyToShip].
     */
    fun markReadyToShip(id: Long): Fulfillment

    /**
     * No-op, no event, when there is no Fulfillment for orderId or it is FAILED or CANCELLED.
     *
     * With Events: [FulfillmentCancelled].
     */
    fun cancelFulfillment(input: CancelFulfillmentInput): Fulfillment?
}
