package com.arcadiaeditions.catalog.inventory.repository.jpa

import com.arcadiaeditions.catalog.inventory.domain.*
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository

/** Spring Data JPA repository for the StockReservation entity. */
@Suppress("unused")
@Repository
@org.jmolecules.ddd.annotation.Repository
@org.jmolecules.architecture.layered.InfrastructureLayer
interface StockReservationRepository : JpaRepository<StockReservation, Long> {}
