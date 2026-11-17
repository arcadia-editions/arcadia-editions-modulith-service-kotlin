package com.arcadiaeditions.catalog.inventory.events.scheduler

import com.arcadiaeditions.catalog.inventory.domain.ReservationStatus
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import com.arcadiaeditions.catalog.inventory.service.InventoryService
import com.arcadiaeditions.catalog.inventory.service.dtos.ExpireReservationInput
import java.time.Clock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * ReservationExpired timer (ZFL `@actor(Scheduler) @time("10 minutes after OrderCreated")`, ZDL `@timer` on
 * expireReservation): polls the persisted deadline `expiresAt` and runs expireReservation, once per order, for the
 * orders with RESERVED reservations whose deadline has passed. expireReservation releases them only when payment has
 * not started (NO_PAYMENT); once payment has started, payment owns the end.
 */
@Component
@ConditionalOnProperty(
    name = ["catalog-inventory.reservation-expired-job.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@org.jmolecules.architecture.layered.InterfaceLayer
class ReservationExpiredJob(
    private val stockReservationRepository: StockReservationRepository,
    private val inventoryService: InventoryService,
    private val clock: Clock,
) {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${catalog-inventory.reservation-expired-job.fixed-delay:PT30S}")
    fun releaseExpiredReservations() {
        val now = clock.instant()
        val orderIds =
            stockReservationRepository
                .findByStatusAndExpiresAtLessThanEqual(ReservationStatus.RESERVED, now)
                .mapNotNull { it.orderId }
                .distinct()
        orderIds.forEach { orderId ->
            try {
                inventoryService.expireReservation(ExpireReservationInput(orderId))
            } catch (e: RuntimeException) {
                // one order failing does not block the others; it is retried on the next run
                log.warn("ReservationExpired: expireReservation failed for order {}", orderId, e)
            }
        }
    }
}
