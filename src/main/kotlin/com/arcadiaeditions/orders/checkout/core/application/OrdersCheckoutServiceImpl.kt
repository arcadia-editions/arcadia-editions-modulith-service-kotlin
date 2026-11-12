package com.arcadiaeditions.orders.checkout.core.application

import com.arcadiaeditions.orders.checkout.core.application.mappers.*
import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import com.arcadiaeditions.orders.checkout.core.outbound.events.*
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Order]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.Application
open class OrdersCheckoutServiceImpl(
    private val orderRepository: OrderRepository,
    private val eventPublisher: EventPublisher,
) : OrdersCheckoutService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val ordersCheckoutServiceMapper: OrdersCheckoutServiceMapper = OrdersCheckoutServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun startOrderCheckout(input: StartOrderCheckoutInput): Order {
        // TODO: call CatalogInventoryZdl InventoryService.reserveStock(ReserveStockInput) -> ReserveStockResult

        log.debug("Request startOrderCheckout: {}", input)

        val order = ordersCheckoutServiceMapper.update(Order(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onOrderCreated(eventsMapper.asOrderCreated(order))
        eventPublisher.onStockUnavailable(eventsMapper.asStockUnavailable(order))
        return order
    }

    override fun confirmOrder(input: ConfirmOrderInput): Order {
        // TODO: call CatalogInventoryZdl InventoryService.confirmStockReservation(ConfirmStockReservationInput) ->
        // StockReservation
        //       then emit one of: StockReservationConfirmed

        log.debug("Request confirmOrder: {}", input)

        // TODO CUSTOM_REQUIRED: load the Order this transition applies to, e.g. by a correlation key in the input
        val foundOrder: Order? = emptyList<Order>().firstOrNull()
        return foundOrder
            ?.let { existingOrder ->
                OrderTransitions.ensureCanConfirmOrder(existingOrder)
                ordersCheckoutServiceMapper.update(existingOrder, input)
                existingOrder.status = OrderStatus.CONFIRMED
                existingOrder
            }
            ?.let { orderRepository.save(it) }
            ?.also {
                // emit events
                eventPublisher.onOrderConfirmed(eventsMapper.asOrderConfirmed(it))
            } ?: throw NoSuchElementException("Order not found")
    }

    override fun cancelOrder(input: CancelOrderInput): Order {

        log.debug("Request cancelOrder: {}", input)

        // TODO CUSTOM_REQUIRED: load the Order this transition applies to, e.g. by a correlation key in the input
        val foundOrder: Order? = emptyList<Order>().firstOrNull()
        return foundOrder
            ?.let { existingOrder ->
                OrderTransitions.ensureCanCancelOrder(existingOrder)
                ordersCheckoutServiceMapper.update(existingOrder, input)
                existingOrder.status = OrderStatus.CANCELLED
                existingOrder
            }
            ?.let { orderRepository.save(it) }
            ?.also {
                // emit events
                eventPublisher.onOrderCancelled(eventsMapper.asOrderCancelled(it))
            } ?: throw NoSuchElementException("Order not found")
    }

    @Transactional(readOnly = true)
    override fun getOrder(id: Long): Order? {

        log.debug("[CRUD] Request to get Order : {}", id)
        val order = orderRepository.findByIdOrNull(id)
        return order
    }
}
