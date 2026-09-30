package com.arcadiaeditions.payments.processing.events.checkout.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.payments.processing.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

/**
 * MapStruct mapper for OrdersCheckoutEventsListener: event -> command input.
 *
 * This file is generated once. Add @Mapping annotations or concrete methods here when MapStruct cannot infer a
 * conversion.
 */
@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutEventsListenerMapper {

    companion object {
        val INSTANCE: OrdersCheckoutEventsListenerMapper = Mappers.getMapper(OrdersCheckoutEventsListenerMapper::class.java)
    }

    fun asAuthorizePaymentInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.OrderCreated
    ): com.arcadiaeditions.payments.processing.service.dtos.AuthorizePaymentInput
}
