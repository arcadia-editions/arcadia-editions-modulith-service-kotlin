package com.arcadiaeditions.orders.checkout.core.application

import com.arcadiaeditions.orders.checkout.core.application.mappers.*
import com.arcadiaeditions.orders.checkout.core.domain.*
import com.arcadiaeditions.orders.checkout.core.domain.events.*
import com.arcadiaeditions.orders.checkout.core.inbound.*
import com.arcadiaeditions.orders.checkout.core.inbound.dtos.*
import com.arcadiaeditions.orders.checkout.core.outbound.events.*
import com.arcadiaeditions.orders.checkout.core.outbound.jpa.*
import com.arcadiaeditions.catalog.inventory.domain.ConfirmStockReservationOutcome
import com.arcadiaeditions.catalog.inventory.domain.ReserveStockOutcome
import com.arcadiaeditions.catalog.inventory.service.dtos.ConfirmStockReservationInput
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

    @Transactional(noRollbackFor = [StockUnavailableException::class])
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
            else -> {
                // A-05: no Order is persisted; the event is emitted and the caller learns through the exception
                val unavailable = result.unavailable
                val event =
                    StockUnavailable(
                        orderId = orderId,
                        customerId = input.customerId,
                        customerEmail = input.customerEmail,
                        unavailableItems =
                            unavailable?.unavailableItems
                                ?.map { StockUnavailableItem(it.sku, it.requestedQuantity, it.availableQuantity) }
                                ?.toMutableList() ?: mutableListOf(),
                    )
                eventPublisher.onStockUnavailable(event)
                throw StockUnavailableException(event)
            }
        }
    }

    override fun confirmOrder(input: ConfirmOrderInput): Order {

        log.debug("Request confirmOrder: {}", input)

        val order = orderRepository.findByOrderId(input.orderId) ?: throw NoSuchElementException("Order not found")
        // redelivery: an already CONFIRMED order does nothing
        if (order.status == OrderStatus.CONFIRMED) {
            return order
        }
        // CANCELLED: rejected without calling inventory
        if (order.status == OrderStatus.CANCELLED) {
            return reject(order, "ORDER_CANCELLED")
        }
        OrderTransitions.ensureCanConfirmOrder(order)
        val result = inventoryService.confirmStockReservation(ConfirmStockReservationInput(orderId = input.orderId))
        if (result.outcome == ConfirmStockReservationOutcome.NOT_HELD) {
            return reject(order, "STOCK_NOT_HELD")
        }
        order.status = OrderStatus.CONFIRMED
        order.confirmedAt = Instant.now(clock)
        val saved = orderRepository.save(order)
        eventPublisher.onOrderConfirmed(eventsMapper.asOrderConfirmed(saved))
        return saved
    }

    private fun reject(order: Order, reason: String): Order {
        val event = eventsMapper.asOrderConfirmationRejected(order)
        event.reason = reason
        event.rejectedAt = Instant.now(clock)
        eventPublisher.onOrderConfirmationRejected(event)
        return order
    }

    override fun cancelOrder(input: CancelOrderInput): Order {

        log.debug("Request cancelOrder: {}", input)

        val order = orderRepository.findByOrderId(input.orderId) ?: throw NoSuchElementException("Order not found")
        // redelivery: an already CANCELLED order does nothing
        if (order.status == OrderStatus.CANCELLED) {
            return order
        }
        OrderTransitions.ensureCanCancelOrder(order)
        order.status = OrderStatus.CANCELLED
        order.cancelledAt = Instant.now(clock)
        order.cancellationReason = input.reason // A-07: StockReleased.reason
        val saved = orderRepository.save(order)
        eventPublisher.onOrderCancelled(eventsMapper.asOrderCancelled(saved))
        return saved
    }

    @Transactional(readOnly = true)
    override fun getOrder(id: Long): Order? {

        log.debug("[CRUD] Request to get Order : {}", id)
        val order = orderRepository.findByIdOrNull(id)
        return order
    }
}
