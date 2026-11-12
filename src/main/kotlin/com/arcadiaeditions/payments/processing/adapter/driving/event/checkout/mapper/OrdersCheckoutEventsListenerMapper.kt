package com.arcadiaeditions.payments.processing.adapter.driving.event.checkout.mapper

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.event.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutEventsListenerMapper {

    companion object {
        val INSTANCE: OrdersCheckoutEventsListenerMapper =
            Mappers.getMapper(OrdersCheckoutEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asAuthorizePaymentInput(
        event: com.arcadiaeditions.orders.checkout.core.domain.events.OrderCreated
    ): com.arcadiaeditions.payments.processing.application.port.driving.dto.AuthorizePaymentInput
}
