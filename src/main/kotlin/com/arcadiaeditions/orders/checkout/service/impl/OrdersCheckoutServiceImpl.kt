package com.arcadiaeditions.orders.checkout.service.impl

import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*
import com.arcadiaeditions.orders.checkout.events.*
import com.arcadiaeditions.orders.checkout.repository.jpa.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import com.arcadiaeditions.orders.checkout.service.impl.mappers.*
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Order]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class OrdersCheckoutServiceImpl(
    private val orderRepository: OrderRepository,
    private val eventPublisher: EventPublisher,
) : OrdersCheckoutService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val ordersCheckoutServiceMapper: OrdersCheckoutServiceMapper = OrdersCheckoutServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun startOrderCheckout(input: StartOrderCheckoutInput): Order {
        // TODO: call CatalogInventoryZdl InventoryService.reserveStock(ReserveStockInput) -> StockReservedResult

        log.debug("Request startOrderCheckout: {}", input)

        val order = ordersCheckoutServiceMapper.update(Order(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onOrderCreated(eventsMapper.asOrderCreated(order))
        eventPublisher.onStockUnavailable(eventsMapper.asStockUnavailable(order))
        return order
    }

    override fun confirmOrder(input: ConfirmOrderInput): Order {
        // TODO: call CatalogInventoryZdl InventoryService.confirmStockReservation(ReserveStockInput) -> Unit
        //       then emit one of: StockReservationConfirmed

        log.debug("Request confirmOrder: {}", input)

        val order = ordersCheckoutServiceMapper.update(Order(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onOrderConfirmed(eventsMapper.asOrderConfirmed(order))
        return order
    }

    override fun cancelOrder(input: CancelOrderInput): Order {

        log.debug("Request cancelOrder: {}", input)

        val order = ordersCheckoutServiceMapper.update(Order(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onOrderCancelled(eventsMapper.asOrderCancelled(order))
        return order
    }
}
