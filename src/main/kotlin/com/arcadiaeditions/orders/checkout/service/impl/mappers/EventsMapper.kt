package com.arcadiaeditions.orders.checkout.service.impl.mappers

import com.arcadiaeditions.common.mappers.BaseMapper
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.StockUnavailableItem
import com.arcadiaeditions.orders.checkout.service.dtos.*
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    // G-OC: OrderCreated.amount is Order.totalAmount, renamed to match AuthorizePaymentInput (see ZDL comment).
    @Mapping(target = "amount", source = "totalAmount")
    fun asOrderCreated(order: Order): com.arcadiaeditions.orders.checkout.domain.events.OrderCreated

    // items[sku,quantity] and shippingAddress{...} map field-by-field automatically (nested types,
    // same property names).
    fun asOrderConfirmed(order: Order): com.arcadiaeditions.orders.checkout.domain.events.OrderConfirmed

    // G-OC: OrderCancelled.reason is Order.cancellationReason.
    @Mapping(target = "reason", source = "cancellationReason")
    fun asOrderCancelled(order: Order): com.arcadiaeditions.orders.checkout.domain.events.OrderCancelled

    // G-OC-05: StockUnavailable can't be filled from the (unsaved) Order - unavailableItems come
    // from InventoryService.reserveStock's result, not from the order. Composed manually from the
    // checkout input/orderId plus the inventory result (skill: "add an overload that takes the extra
    // source").
    fun asStockUnavailableItem(
        line: com.arcadiaeditions.catalog.inventory.service.dtos.StockUnavailableLine
    ): StockUnavailableItem

    fun asStockUnavailable(
        orderId: String?,
        customerId: String?,
        customerEmail: String?,
        result: com.arcadiaeditions.catalog.inventory.service.dtos.StockUnavailableResult,
    ): com.arcadiaeditions.orders.checkout.domain.events.StockUnavailable {
        val event =
            com.arcadiaeditions.orders.checkout.domain.events.StockUnavailable(
                orderId = orderId,
                customerId = customerId,
                customerEmail = customerEmail,
            )
        result.unavailableItems.forEach { event.addUnavailableItems(asStockUnavailableItem(it)) }
        return event
    }
}
