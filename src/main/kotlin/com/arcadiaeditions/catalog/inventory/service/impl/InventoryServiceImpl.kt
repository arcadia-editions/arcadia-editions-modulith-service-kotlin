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
    private val eventPublisher: EventPublisher,
) : InventoryService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val inventoryServiceMapper: InventoryServiceMapper = InventoryServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun reserveStock(input: ReserveStockInput): ReserveStockResult {

        log.debug("Request reserveStock: {}", input)

        val stockReservation = inventoryServiceMapper.update(StockReservation(), input)
        // TODO: implement this method
        return inventoryServiceMapper.asReserveStockResult(stockReservation)
    }

    override fun releaseStock(input: ReleaseStockInput): List<StockReservation> {

        log.debug("Request releaseStock: {}", input)

        val stockReservations = stockReservationRepository.findAll()
        return stockReservations
    }

    override fun confirmStockReservation(input: ConfirmStockReservationInput): List<StockReservation> {

        log.debug("Request confirmStockReservation: {}", input)

        val stockReservations = stockReservationRepository.findAll()
        return stockReservations
    }
}
