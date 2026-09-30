package com.arcadiaeditions.fulfillment.shipping.service.ports

/**
 * Outbound port to the warehouse/carrier system that decides whether `scheduleFulfillment`
 * results in `FulfillmentScheduled` or `FulfillmentFailed` (Q-09). A stub adapter is the only bean
 * for now.
 */
interface FulfillmentCarrier {

    fun schedule(request: ScheduleRequest): ScheduleResult
}

data class ScheduleRequest(
    val orderId: String,
    val customerId: String,
    val postalCode: String,
    val items: List<ScheduleRequestItem>,
)

data class ScheduleRequestItem(val sku: String, val quantity: Int)

enum class ScheduleOutcome {
    SCHEDULED,
    FAILED,
}

data class ScheduleResult(
    val outcome: ScheduleOutcome,
    val carrierCode: String? = null,
    val trackingNumber: String? = null,
    val failureReason: String? = null,
)
