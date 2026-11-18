package com.arcadiaeditions.fulfillment.shipping.processing.mappers

import com.arcadiaeditions.payments.processing.domain.event.PaymentCaptured
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaymentsProcessingEventsListenerMapperTest {

    @Test
    fun paymentCapturedMapsOrderIdAndCapturedAtTest() {
        val capturedAt = Instant.parse("2026-10-01T11:13:05Z")
        val input =
            PaymentsProcessingEventsListenerMapper.INSTANCE.asRecordPaymentCapturedInput(
                PaymentCaptured(orderId = "ORD-005", capturedAt = capturedAt)
            )
        assertEquals("ORD-005", input.orderId)
        assertEquals(capturedAt, input.capturedAt)
    }
}
