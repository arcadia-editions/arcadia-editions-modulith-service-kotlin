package com.arcadiaeditions.payments.processing.adapter.driving.event.processing.mapper

import com.arcadiaeditions.payments.processing.domain.event.PaymentFailed
import com.arcadiaeditions.payments.processing.domain.event.PaymentRetried
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaymentsProcessingServiceEventsListenerMapperTest {

    private val mapper = PaymentsProcessingServiceEventsListenerMapper.INSTANCE

    @Test
    fun asRetryPaymentInputCarriesAttemptCountTest() {
        val input = mapper.asRetryPaymentInput(PaymentFailed(orderId = "ORD-012", customerId = "CUST-012", attemptCount = 2 as Integer))
        assertEquals("ORD-012", input.orderId)
        assertEquals(2, input.attemptCount.toInt())
    }

    @Test
    fun asReauthorizePaymentInputCarriesAttemptCountTest() {
        val input = mapper.asReauthorizePaymentInput(PaymentRetried(orderId = "ORD-012", customerId = "CUST-012", attemptCount = 1 as Integer))
        assertEquals("ORD-012", input.orderId)
        assertEquals(1, input.attemptCount.toInt())
    }
}
