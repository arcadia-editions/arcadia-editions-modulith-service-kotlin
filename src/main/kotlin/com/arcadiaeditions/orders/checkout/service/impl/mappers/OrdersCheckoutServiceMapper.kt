package com.arcadiaeditions.orders.checkout.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface OrdersCheckoutServiceMapper {

    companion object {
        val INSTANCE: OrdersCheckoutServiceMapper = Mappers.getMapper(OrdersCheckoutServiceMapper::class.java)
    }

    // input mappings
    // CancelOrderInputnull-Order cancelOrder

    fun asOrder(input: CancelOrderInput): Order

    @Mapping(target = "id", ignore = true) fun update(@MappingTarget entity: Order, input: CancelOrderInput): Order

    // StartOrderCheckoutInputnull-Order startOrderCheckout

    fun asOrder(input: StartOrderCheckoutInput): Order

    @Mapping(target = "id", ignore = true)
    fun update(@MappingTarget entity: Order, input: StartOrderCheckoutInput): Order

    // ConfirmOrderInputnull-Order confirmOrder

    fun asOrder(input: ConfirmOrderInput): Order

    @Mapping(target = "id", ignore = true) fun update(@MappingTarget entity: Order, input: ConfirmOrderInput): Order
    // output mappings
}
