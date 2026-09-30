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
import java.util.UUID
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
    // G-CI-01/G-CI-02/G-CI-03: needed to move quantities between available/reserved/sold.
    private val inventoryPositionRepository: InventoryPositionRepository,
    private val eventPublisher: EventPublisher,
    private val clock: Clock,
) : InventoryService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val inventoryServiceMapper: InventoryServiceMapper = InventoryServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    companion object {
        /** ZFL: "10 minutes after OrderCreated". */
        val RESERVATION_TTL: Duration = Duration.ofMinutes(10)
    }

    /**
     * ZenWave generates numeric entity/DTO fields as the boxed `java.lang.Integer` type (so they can
     * be `null`), which Kotlin treats as a distinct nominal type from `kotlin.Int` when it's written
     * out explicitly like this (unlike the usual Java-interop flexible mapping). Boxing an arithmetic
     * `Int` result back into one of these fields needs an explicit conversion.
     */
    private fun Int.toBoxed(): Integer = this as Integer

    /**
     * G-CI-01: one [StockReservation] per order line. RESERVED only if every line is available;
     * otherwise nothing is reserved and the outcome is UNAVAILABLE (ZFL `reserveStock`,
     * `@transition(to: RESERVED)`). `reserveStock` itself does not emit an event - see the ZFL/ZDL,
     * inventory events are only emitted by `confirmStockReservation`/`releaseStock`.
     *
     * Idempotent redelivery: if reservations already exist for this `orderId` (e.g. a retried
     * `startOrderCheckout` call), return them as RESERVED without reserving again.
     *
     * Concurrency: [InventoryPosition] and [StockReservation] both carry JPA `@Version`, so two
     * concurrent reservations racing on the same SKU during a drop fail with an optimistic-locking
     * exception on `save` rather than silently overselling; the caller can retry.
     */
    override fun reserveStock(input: ReserveStockInput): ReserveStockResult {

        log.debug("Request reserveStock: {}", input)

        val existing = stockReservationRepository.findByOrderId(input.orderId)
        if (existing.isNotEmpty()) {
            log.debug("Idempotent redelivery of reserveStock for order {}", input.orderId)
            return inventoryServiceMapper.asReserveStockResult(input.orderId, existing.first().expiresAt, existing)
        }

        val positionsBySku = input.items.associate { it.sku to inventoryPositionRepository.findBySku(it.sku) }

        val unavailable =
            input.items.mapNotNull { item ->
                val position = positionsBySku[item.sku]
                val available = position?.availableQuantity?.toInt() ?: 0
                if (position == null || available < item.quantity.toInt()) {
                    StockUnavailableLine(
                        sku = item.sku,
                        requestedQuantity = item.quantity,
                        availableQuantity = available.toBoxed(),
                    )
                } else {
                    null
                }
            }

        if (unavailable.isNotEmpty()) {
            log.debug("Stock unavailable for order {}: {}", input.orderId, unavailable)
            return ReserveStockResult(
                outcome = ReserveStockOutcome.UNAVAILABLE,
                reserved = null,
                unavailable = StockUnavailableResult(orderId = input.orderId, unavailableItems = unavailable.toMutableList()),
            )
        }

        val now = Instant.now(clock)
        val expiresAt = now.plus(RESERVATION_TTL)

        val reservations =
            input.items.map { item ->
                val position = positionsBySku.getValue(item.sku)!!
                position.availableQuantity = (position.availableQuantity!!.toInt() - item.quantity.toInt()).toBoxed()
                position.reservedQuantity = (position.reservedQuantity!!.toInt() + item.quantity.toInt()).toBoxed()
                StockReservation(
                    reservationId = "RES-${UUID.randomUUID()}",
                    orderId = input.orderId,
                    sku = item.sku,
                    quantity = item.quantity,
                    status = ReservationStatus.RESERVED,
                    reservedAt = now,
                    expiresAt = expiresAt,
                )
            }

        inventoryPositionRepository.saveAll(positionsBySku.values.filterNotNull())
        val saved = stockReservationRepository.saveAll(reservations)

        return inventoryServiceMapper.asReserveStockResult(input.orderId, expiresAt, saved)
    }

    /**
     * G-CI-02/M-10: releases every reservation of the order back to `available`. Reservations
     * already RELEASED are skipped (idempotent: payment decline/retry-exhausted/voided and the
     * expiry timer can all race to release the same order). If every reservation is already
     * released, nothing is mutated and no event is emitted (Q-13).
     */
    override fun releaseStock(input: ReleaseStockInput): List<StockReservation> {

        log.debug("Request releaseStock: {}", input)

        val reservations = stockReservationRepository.findByOrderId(input.orderId)
        if (reservations.isEmpty()) {
            throw NoSuchElementException("StockReservation not found for order: ${input.orderId}")
        }

        // Captured before mutating anything below: `toRelease`/`alreadyReleased` share object
        // identity with `reservations` (entities are mutated in place), so this split must happen
        // before any status is changed - otherwise a just-released reservation would satisfy both
        // predicates and get counted twice in the returned list.
        val toRelease = reservations.filter { it.status != ReservationStatus.RELEASED }
        val alreadyReleased = reservations.filter { it.status == ReservationStatus.RELEASED }
        if (toRelease.isEmpty()) {
            log.debug("Idempotent redelivery of releaseStock for order {}: already released", input.orderId)
            return reservations
        }

        toRelease.forEach { StockReservationTransitions.ensureCanReleaseStock(it) }

        val now = Instant.now(clock)
        val released =
            toRelease.map { reservation ->
                val position =
                    inventoryPositionRepository.findBySku(reservation.sku!!)
                        ?: throw NoSuchElementException("InventoryPosition not found for sku: ${reservation.sku}")
                val quantity = reservation.quantity!!.toInt()
                if (reservation.status == ReservationStatus.CONFIRMED) {
                    position.soldQuantity = (position.soldQuantity!!.toInt() - quantity).toBoxed()
                } else {
                    position.reservedQuantity = (position.reservedQuantity!!.toInt() - quantity).toBoxed()
                }
                position.availableQuantity = (position.availableQuantity!!.toInt() + quantity).toBoxed()
                inventoryPositionRepository.save(position)

                reservation.status = ReservationStatus.RELEASED
                reservation.releasedAt = now
                reservation.releaseReason = input.reason
                reservation
            }

        val savedReleased = stockReservationRepository.saveAll(released)
        eventPublisher.onStockReleased(eventsMapper.asStockReleased(input.orderId, input.reason, now, savedReleased))

        return savedReleased + alreadyReleased
    }

    /**
     * G-CI-03/M-06: confirms every reservation of the order (Q-08: reserved -> sold). Idempotent: if
     * every reservation is already CONFIRMED, return them without moving stock again or emitting a
     * second event.
     */
    override fun confirmStockReservation(input: ConfirmStockReservationInput): List<StockReservation> {

        log.debug("Request confirmStockReservation: {}", input)

        val reservations = stockReservationRepository.findByOrderId(input.orderId)
        if (reservations.isEmpty()) {
            throw NoSuchElementException("StockReservation not found for order: ${input.orderId}")
        }

        if (reservations.all { it.status == ReservationStatus.CONFIRMED }) {
            log.debug("Idempotent redelivery of confirmStockReservation for order {}: already confirmed", input.orderId)
            return reservations
        }

        reservations.forEach { StockReservationTransitions.ensureCanConfirmStockReservation(it) }

        val now = Instant.now(clock)
        val confirmed =
            reservations.map { reservation ->
                val position =
                    inventoryPositionRepository.findBySku(reservation.sku!!)
                        ?: throw NoSuchElementException("InventoryPosition not found for sku: ${reservation.sku}")
                val quantity = reservation.quantity!!.toInt()
                position.reservedQuantity = (position.reservedQuantity!!.toInt() - quantity).toBoxed()
                position.soldQuantity = (position.soldQuantity!!.toInt() + quantity).toBoxed()
                inventoryPositionRepository.save(position)

                reservation.status = ReservationStatus.CONFIRMED
                reservation.confirmedAt = now
                reservation
            }

        val saved = stockReservationRepository.saveAll(confirmed)
        eventPublisher.onStockReservationConfirmed(eventsMapper.asStockReservationConfirmed(input.orderId, now, saved))
        return saved
    }
}
