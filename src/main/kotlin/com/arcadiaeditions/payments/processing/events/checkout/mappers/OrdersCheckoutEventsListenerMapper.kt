package com.arcadiaeditions.payments.processing.events.checkout.mappers

import com.arcadiaeditions.payments.processing.domain.events.*

/**
 * Regenerated mapping contract for OrdersCheckoutEventsListener. Customize the mappings in the generated-once
 * OrdersCheckoutEventsListenerMapStructMapper.
 */
interface OrdersCheckoutEventsListenerMapper {

    fun asAuthorizePaymentInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.OrderCreated
    ): com.arcadiaeditions.payments.processing.service.dtos.AuthorizePaymentInput
}
