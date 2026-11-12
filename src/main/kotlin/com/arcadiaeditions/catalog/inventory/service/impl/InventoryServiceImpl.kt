package com.arcadiaeditions.catalog.inventory.service.impl

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.catalog.inventory.events.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.catalog.inventory.service.impl.mappers.*
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [StockReservation]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class InventoryServiceImpl(
    private val stockReservationRepository: StockReservationRepository,
    private val inventoryPositionRepository: InventoryPositionRepository,
    private val eventPublisher: EventPublisher,
    private val clock: java.time.Clock,
) : InventoryService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val inventoryServiceMapper: InventoryServiceMapper = InventoryServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun reserveStock(input: ReserveStockInput): ReserveStockResult {

        log.debug("Request reserveStock: {}", input)

        // all-or-nothing: check every line first (lines of the same sku add up)
        val requested = input.items.groupBy { it.sku!! }.mapValues { (_, lines) -> lines.sumOf { it.quantity!!.toInt() } }
        val positions = requested.keys.associateWith { inventoryPositionRepository.findBySku(it) }
        val short =
            input.items.filter { line ->
                val available = positions[line.sku!!]?.availableQuantity?.toInt() ?: 0
                requested.getValue(line.sku!!) > available
            }
        if (short.isNotEmpty()) {
            val unavailable = StockUnavailableResult(orderId = input.orderId)
            short.distinctBy { it.sku }.forEach { line ->
                unavailable.addUnavailableItems(
                    StockUnavailableLine(
                        sku = line.sku,
                        requestedQuantity = requested.getValue(line.sku!!) as Integer,
                        availableQuantity = (positions[line.sku!!]?.availableQuantity?.toInt() ?: 0) as Integer,
                    )
                )
            }
            return ReserveStockResult(ReserveStockOutcome.UNAVAILABLE, null, unavailable)
        }

        val now = clock.instant()
        val expiresAt = now.plus(Duration.ofMinutes(10))
        input.items.forEach { line ->
            val position = positions.getValue(line.sku!!)!!
            val quantity = line.quantity!!.toInt()
            position.availableQuantity = (position.availableQuantity!!.toInt() - quantity) as Integer
            position.reservedQuantity = (position.reservedQuantity!!.toInt() + quantity) as Integer
        }
        inventoryPositionRepository.saveAll(positions.values.filterNotNull())
        val reservations =
            stockReservationRepository.saveAll(
                input.items.map { line ->
                    StockReservation(
                        reservationId = "RES-" + java.util.UUID.randomUUID(),
                        orderId = input.orderId,
                        sku = line.sku,
                        quantity = line.quantity,
                        status = ReservationStatus.RESERVED,
                        reservedAt = now,
                        expiresAt = expiresAt,
                    )
                }
            )
        val reserved = StockReservedResult(orderId = input.orderId, expiresAt = expiresAt)
        reservations.forEach {
            reserved.addReservations(StockReservedLine(it.reservationId, it.sku, it.quantity, it.reservedAt))
        }
        return ReserveStockResult(ReserveStockOutcome.RESERVED, reserved, null)
    }

    override fun releaseStock(input: ReleaseStockInput): List<StockReservation> {

        log.debug("Request releaseStock: {}", input)

        // TODO CUSTOM_REQUIRED: load the StockReservation(s) this transition applies to, e.g. by a correlation key in
        // the input
        var stockReservations: List<StockReservation> = emptyList()
        stockReservations.forEach { existingStockReservation ->
            StockReservationTransitions.ensureCanReleaseStock(existingStockReservation)
            inventoryServiceMapper.update(existingStockReservation, input)
            existingStockReservation.status = ReservationStatus.RELEASED
        }
        stockReservations = stockReservationRepository.saveAll(stockReservations)
        // emit events
        stockReservations.forEach { eventSource ->
            eventPublisher.onStockReleased(eventsMapper.asStockReleased(eventSource))
        }
        return stockReservations
    }

    override fun confirmStockReservation(input: ConfirmStockReservationInput): List<StockReservation> {

        log.debug("Request confirmStockReservation: {}", input)

        val all = stockReservationRepository.findByOrderId(input.orderId!!)
        // redelivery: only RESERVED reservations transition, CONFIRMED ones are left alone
        val toConfirm = all.filter { it.status == ReservationStatus.RESERVED }
        if (toConfirm.isEmpty()) {
            return all
        }
        val now = clock.instant()
        toConfirm.forEach { reservation ->
            StockReservationTransitions.ensureCanConfirmStockReservation(reservation)
            reservation.status = ReservationStatus.CONFIRMED
            reservation.confirmedAt = now
            // reserved -> sold
            val position = inventoryPositionRepository.findBySku(reservation.sku!!)!!
            val quantity = reservation.quantity!!.toInt()
            position.reservedQuantity = (position.reservedQuantity!!.toInt() - quantity) as Integer
            position.soldQuantity = (position.soldQuantity!!.toInt() + quantity) as Integer
            inventoryPositionRepository.save(position)
        }
        val confirmed = stockReservationRepository.saveAll(toConfirm)
        // one event for the order, with every confirmed line
        val event = StockReservationConfirmed(orderId = input.orderId, confirmedAt = now)
        confirmed.forEach {
            event.addItems(StockReservationConfirmedItem(it.reservationId, it.sku, it.quantity))
        }
        eventPublisher.onStockReservationConfirmed(event)
        return all
    }
}
