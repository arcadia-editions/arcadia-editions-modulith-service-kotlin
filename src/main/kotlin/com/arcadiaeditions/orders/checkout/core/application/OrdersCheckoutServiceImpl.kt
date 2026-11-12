package com.arcadiaeditions.orders.checkout.core.application

import com.arcadiaeditions.orders.checkout.core.application.mappers.*
import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import com.arcadiaeditions.orders.checkout.core.outbound.events.*
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*
import com.arcadiaeditions.catalog.inventory.domain.ReserveStockOutcome
import com.arcadiaeditions.catalog.inventory.service.dtos.ReserveStockInput
import com.arcadiaeditions.catalog.inventory.service.dtos.ReserveStockItemInput
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
    private val inventoryService: com.arcadiaeditions.catalog.inventory.service.InventoryService,
    private val eventPublisher: EventPublisher,
    private val clock: java.time.Clock,
) : OrdersCheckoutService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val ordersCheckoutServiceMapper: OrdersCheckoutServiceMapper = OrdersCheckoutServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun startOrderCheckout(input: StartOrderCheckoutInput): Order {
        log.debug("Request startOrderCheckout: {}", input)

        val orderId = "ORD-" + java.util.UUID.randomUUID()
        val reserveStockInput =
            ReserveStockInput(
                orderId = orderId,
                items = input.items.map { ReserveStockItemInput(it.sku, it.quantity) }.toMutableList(),
            )
        val result = inventoryService.reserveStock(reserveStockInput)
        when (result.outcome) {
            ReserveStockOutcome.RESERVED -> {
                val order = ordersCheckoutServiceMapper.update(Order(), input)
                order.orderId = orderId
                order.status = OrderStatus.CREATED
                order.totalAmount =
                    order.items.fold(BigDecimal.ZERO) { sum, item ->
                        sum + BigDecimal.valueOf(item.quantity!!.toLong()).multiply(item.unitPrice!!)
                    }
                order.createdAt = Instant.now(clock)
                val saved = orderRepository.save(order)
                eventPublisher.onOrderCreated(eventsMapper.asOrderCreated(saved))
                return saved
            }
            // UNAVAILABLE is T-14
            else -> TODO("StockUnavailable branch: T-14")
        }
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
