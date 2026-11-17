package com.arcadiaeditions.payments.processing.adapter.driving.event.checkout.mapper

import com.arcadiaeditions.orders.checkout.core.domain.events.OrderConfirmationRejected
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrdersCheckoutEventsListenerMapperTest {

    @Test
    fun orderConfirmationRejectedMapsOrderIdAndRejectionReasonAsVoidReasonTest() {
        val input =
            OrdersCheckoutEventsListenerMapper.INSTANCE.asVoidPaymentInput(
                OrderConfirmationRejected(
                    orderId = "ORD-022",
                    customerId = "CUST-022",
                    reason = "ORDER_CANCELLED",
                    rejectedAt = Instant.parse("2026-10-01T12:00:00Z"),
                )
            )
        assertEquals("ORD-022", input.orderId)
        assertEquals("ORDER_CANCELLED", input.reason)
    }
}
