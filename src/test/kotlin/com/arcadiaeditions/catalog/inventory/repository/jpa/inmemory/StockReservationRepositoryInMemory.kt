package com.arcadiaeditions.catalog.inventory.repository.jpa.inmemory

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.StockReservationRepository
import java.math.*
import java.time.*
import java.util.*

class StockReservationRepositoryInMemory :
    InMemoryJpaRepository<StockReservation, Long>(), StockReservationRepository {

    override fun findByOrderId(orderId: String): List<StockReservation> = findAll().filter { it.orderId == orderId }

    override fun findByStatusAndExpiresAtLessThanEqual(status: ReservationStatus, expiresAt: Instant): List<StockReservation> =
        findAll().filter { it.status == status && it.expiresAt != null && !it.expiresAt!!.isAfter(expiresAt) }
}
