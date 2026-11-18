package com.arcadiaeditions.fulfillment.shipping.checkout.mappers

import com.arcadiaeditions.orders.checkout.core.domain.events.OrderCancelled
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrdersCheckoutEventsListenerMapperTest {

    @Test
    fun orderCancelledMapsOrderIdAndReasonTest() {
        val input =
            OrdersCheckoutEventsListenerMapper.INSTANCE.asCancelFulfillmentInput(
                OrderCancelled(
                    orderId = "ORD-025",
                    customerId = "CUST-025",
                    reason = "PAYMENT_VOIDED",
                    cancelledAt = Instant.parse("2026-10-01T09:38:30Z"),
                )
            )
        assertEquals("ORD-025", input.orderId)
        assertEquals("PAYMENT_VOIDED", input.reason)
    }
}
