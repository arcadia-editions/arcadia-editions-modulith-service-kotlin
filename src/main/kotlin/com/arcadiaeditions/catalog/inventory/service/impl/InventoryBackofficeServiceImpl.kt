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

/** Service Implementation for managing [InventoryPosition]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class InventoryBackofficeServiceImpl(
    private val inventoryPositionRepository: InventoryPositionRepository,
    private val eventPublisher: EventPublisher,
) : InventoryBackofficeService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val inventoryBackofficeServiceMapper: InventoryBackofficeServiceMapper =
        InventoryBackofficeServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun createInventoryPosition(input: CreateInventoryPositionInput): InventoryPosition {

        log.debug("[CRUD] Request to save InventoryPosition: {}", input)
        var inventoryPosition = inventoryBackofficeServiceMapper.update(InventoryPosition(), input)
        inventoryPosition = inventoryPositionRepository.save(inventoryPosition)
        // TODO: may need to reload the entity to fetch relationships 'mapped by id'
        return inventoryPosition
    }

    override fun receiveStock(input: ReceiveStockInput): InventoryPosition {

        log.debug("Request receiveStock: {}", input)

        val inventoryPosition = inventoryBackofficeServiceMapper.update(InventoryPosition(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onStockReceived(eventsMapper.asStockReceived(inventoryPosition))
        return inventoryPosition
    }

    override fun adjustStock(input: AdjustStockInput): InventoryPosition {

        log.debug("Request adjustStock: {}", input)

        val inventoryPosition = inventoryBackofficeServiceMapper.update(InventoryPosition(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onStockAdjusted(eventsMapper.asStockAdjusted(inventoryPosition))
        return inventoryPosition
    }
}
