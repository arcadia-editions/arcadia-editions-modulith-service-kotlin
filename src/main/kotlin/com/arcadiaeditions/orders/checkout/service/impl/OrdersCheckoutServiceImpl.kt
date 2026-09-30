package com.arcadiaeditions.orders.checkout.service.impl

import com.arcadiaeditions.catalog.inventory.domain.ReserveStockOutcome
import com.arcadiaeditions.catalog.inventory.service.InventoryService
import com.arcadiaeditions.catalog.inventory.service.dtos.ConfirmStockReservationInput
import com.arcadiaeditions.catalog.inventory.service.dtos.ReserveStockInput
import com.arcadiaeditions.catalog.inventory.service.dtos.ReserveStockItemInput
import com.arcadiaeditions.orders.checkout.domain.*
import com.arcadiaeditions.orders.checkout.domain.events.*
import com.arcadiaeditions.orders.checkout.events.*
import com.arcadiaeditions.orders.checkout.repository.jpa.*
import com.arcadiaeditions.orders.checkout.service.*
import com.arcadiaeditions.orders.checkout.service.dtos.*
import com.arcadiaeditions.orders.checkout.service.impl.mappers.*
import java.math.*
import java.time.*
import java.util.UUID
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
    private val clock: Clock,
    // G-OC-01/X-04: cross-module call through the InventoryService interface only (Spring Modulith
    // named interface), never its repository or impl.
    private val inventoryService: InventoryService,
) : OrdersCheckoutService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val ordersCheckoutServiceMapper: OrdersCheckoutServiceMapper = OrdersCheckoutServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    /**
     * G-OC-01/G-OC-02: reserves stock first (ZDL `@calls(InventoryService.reserveStock)`), then
     * branches on the outcome. RESERVED -> the order is created and `OrderCreated` is the only event
     * emitted. UNAVAILABLE -> nothing is persisted (Q-03/Q-15), `StockUnavailable` is emitted and
     * `StockUnavailableException` is thrown so the web layer answers 409.
     *
     * Q-03: the event must survive even though the method throws afterwards. `startOrderCheckout`
     * doesn't write anything to `order_repository` in the UNAVAILABLE branch, but Spring Modulith's
     * event publication registry still records/removes the publication inside this same
     * transaction; a default rollback on any `RuntimeException` would also undo that registration
     * and the event would never be (re)delivered. `noRollbackFor` keeps the transaction committing on
     * `StockUnavailableException` specifically, while every other exception (a genuine failure) still
     * rolls back as normal.
     */
    @Transactional(noRollbackFor = [StockUnavailableException::class])
    override fun startOrderCheckout(input: StartOrderCheckoutInput): Order {

        log.debug("Request startOrderCheckout: {}", input)

        val orderId = "ORD-${UUID.randomUUID()}"
        val reserveStockInput =
            ReserveStockInput(
                orderId = orderId,
                items = input.items.map { ReserveStockItemInput(it.sku, it.quantity) }.toMutableList(),
            )
        val result = inventoryService.reserveStock(reserveStockInput)

        return when (result.outcome) {
            ReserveStockOutcome.UNAVAILABLE -> {
                val unavailable =
                    result.unavailable
                        ?: throw IllegalStateException("reserveStock UNAVAILABLE without an unavailable result")
                eventPublisher.onStockUnavailable(
                    eventsMapper.asStockUnavailable(orderId, input.customerId, input.customerEmail, unavailable)
                )
                throw StockUnavailableException("Stock unavailable for order $orderId")
            }
            ReserveStockOutcome.RESERVED -> {
                val order = ordersCheckoutServiceMapper.update(Order(), input)
                order.orderId = orderId
                order.status = OrderStatus.CREATED
                order.totalAmount =
                    input.items.fold(BigDecimal.ZERO) { total, item ->
                        total + item.unitPrice.multiply(BigDecimal(item.quantity.toInt()))
                    }
                order.createdAt = Instant.now(clock)
                val saved = orderRepository.save(order)
                eventPublisher.onOrderCreated(eventsMapper.asOrderCreated(saved))
                saved
            }
        }
    }

    /**
     * G-OC-03: confirms the stock reservation (ZDL `@calls(InventoryService.confirmStockReservation)`)
     * before moving the order to CONFIRMED. Idempotent: an order already CONFIRMED (e.g. a
     * re-delivered `PaymentAuthorized`) is returned as-is (X-07: the generated TODO wrongly suggested
     * emitting `StockReservationConfirmed` here - that event belongs to inventory, not orders).
     */
    override fun confirmOrder(input: ConfirmOrderInput): Order {

        log.debug("Request confirmOrder: {}", input)

        val order =
            orderRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Order not found: ${input.orderId}")
        if (order.status == OrderStatus.CONFIRMED) {
            log.debug("Idempotent redelivery of confirmOrder for order {}", input.orderId)
            return order
        }
        OrderTransitions.ensureCanConfirmOrder(order)

        inventoryService.confirmStockReservation(ConfirmStockReservationInput(input.orderId))

        order.status = OrderStatus.CONFIRMED
        order.confirmedAt = Instant.now(clock)
        val saved = orderRepository.save(order)
        eventPublisher.onOrderConfirmed(eventsMapper.asOrderConfirmed(saved))
        return saved
    }

    /**
     * G-OC-04/M-07: cancels the order once its stock has been released (ZFL: `cancelOrder` is
     * triggered `when StockReleased`). Idempotent: an already-CANCELLED order (e.g. `StockReleased`
     * redelivered) is a no-op - no event, no exception.
     */
    override fun cancelOrder(input: CancelOrderInput): Order {

        log.debug("Request cancelOrder: {}", input)

        val order =
            orderRepository.findByOrderId(input.orderId)
                ?: throw NoSuchElementException("Order not found: ${input.orderId}")
        if (order.status == OrderStatus.CANCELLED) {
            log.debug("Idempotent redelivery of cancelOrder for order {}", input.orderId)
            return order
        }
        OrderTransitions.ensureCanCancelOrder(order)

        order.status = OrderStatus.CANCELLED
        order.cancelledAt = Instant.now(clock)
        order.cancellationReason = input.reason
        val saved = orderRepository.save(order)
        eventPublisher.onOrderCancelled(eventsMapper.asOrderCancelled(saved))
        return saved
    }
}
