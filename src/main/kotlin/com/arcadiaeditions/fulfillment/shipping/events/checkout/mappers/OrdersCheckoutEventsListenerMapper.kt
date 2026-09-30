package com.arcadiaeditions.fulfillment.shipping.events.checkout.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
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

    fun asScheduleFulfillmentInput(
        event: com.arcadiaeditions.orders.checkout.domain.events.OrderConfirmed
    ): com.arcadiaeditions.fulfillment.shipping.service.dtos.ScheduleFulfillmentInput
}
