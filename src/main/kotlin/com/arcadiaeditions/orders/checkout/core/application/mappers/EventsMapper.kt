package com.arcadiaeditions.orders.checkout.core.application.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    fun asOrderCreated(order: Order): com.arcadiaeditions.orders.checkout.core.domain.events.OrderCreated

    fun asOrderConfirmed(order: Order): com.arcadiaeditions.orders.checkout.core.domain.events.OrderConfirmed

    fun asOrderCancelled(order: Order): com.arcadiaeditions.orders.checkout.core.domain.events.OrderCancelled

    fun asStockUnavailable(order: Order): com.arcadiaeditions.orders.checkout.core.domain.events.StockUnavailable
}
