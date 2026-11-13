package com.arcadiaeditions.payments.processing.adapter.driving.event.shipping.mapper

import com.arcadiaeditions.fulfillment.shipping.domain.events.FulfillmentFailed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FulfillmentShippingEventsListenerMapperTest {

    @Test
    fun voidReasonComesFromEventTypeTest() {
        // A-06, C-10: Fulfillment.failureReason warehouse_damage, Payment.failureReason fulfillment_failed
        val input =
            FulfillmentShippingEventsListenerMapper.INSTANCE.asVoidPaymentInput(
                FulfillmentFailed(fulfillmentId = "FUL-010", orderId = "ORD-010", reason = "warehouse_damage")
            )
        assertEquals("ORD-010", input.orderId)
        assertEquals("fulfillment_failed", input.reason)
    }
}
