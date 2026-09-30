package com.arcadiaeditions.fulfillment.shipping.adapters.stub

import com.arcadiaeditions.fulfillment.shipping.service.ports.FulfillmentCarrier
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleOutcome
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleRequest
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleResult
import java.util.UUID
import org.springframework.stereotype.Component

/**
 * Deterministic default [FulfillmentCarrier] (Q-09). No real carrier is wired yet, so this stub is
 * the only bean.
 *
 * Outcome is controlled two ways, checked in this order:
 * 1. `forcedOutcome`, settable by tests, always wins when non-null.
 * 2. `ScheduleRequest.postalCode == "FAIL00"` → [ScheduleOutcome.FAILED]. Any other postal code →
 *    [ScheduleOutcome.SCHEDULED].
 */
@Component
class StubFulfillmentCarrier : FulfillmentCarrier {

    var forcedOutcome: ScheduleOutcome? = null

    override fun schedule(request: ScheduleRequest): ScheduleResult {
        val outcome =
            forcedOutcome ?: if (request.postalCode == FAILING_POSTAL_CODE) ScheduleOutcome.FAILED
            else ScheduleOutcome.SCHEDULED
        return when (outcome) {
            ScheduleOutcome.SCHEDULED ->
                ScheduleResult(
                    outcome,
                    carrierCode = "STUB-CARRIER",
                    trackingNumber = "TRACK-${UUID.randomUUID()}",
                )
            ScheduleOutcome.FAILED -> ScheduleResult(outcome, failureReason = "No carrier capacity available")
        }
    }

    companion object {
        const val FAILING_POSTAL_CODE = "FAIL00"
    }
}
