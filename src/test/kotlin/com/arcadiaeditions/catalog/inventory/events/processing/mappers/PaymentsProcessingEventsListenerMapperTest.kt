package com.arcadiaeditions.catalog.inventory.events.processing.mappers

import com.arcadiaeditions.catalog.inventory.domain.StockReleaseReason
import com.arcadiaeditions.catalog.inventory.service.dtos.ReleaseStockInput
import com.arcadiaeditions.payments.processing.domain.event.PaymentVoidFailed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PaymentsProcessingEventsListenerMapperTest {

    private val mapper = PaymentsProcessingEventsListenerMapper.INSTANCE

    @Test
    fun paymentVoidFailedSetsReasonTest() {
        val input = mapper.asReleaseStockInput(PaymentVoidFailed(orderId = "ORD-027", reason = "void_refused"))

        assertEquals(ReleaseStockInput("ORD-027", StockReleaseReason.PAYMENT_VOID_FAILED), input)
    }
}
