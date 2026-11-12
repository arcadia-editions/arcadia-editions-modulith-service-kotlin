package com.arcadiaeditions.fulfillment.shipping.checkout.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutEventsListenerMapper {

    companion object {
        val INSTANCE: OrdersCheckoutEventsListenerMapper =
            Mappers.getMapper(OrdersCheckoutEventsListenerMapper::class.java)
    }

    /** Add `@Mapping` here when MapStruct cannot infer the conversion. */
    fun asScheduleFulfillmentInput(
        event: com.arcadiaeditions.orders.checkout.core.domain.events.OrderConfirmed
    ): com.arcadiaeditions.fulfillment.shipping.dtos.ScheduleFulfillmentInput
}
