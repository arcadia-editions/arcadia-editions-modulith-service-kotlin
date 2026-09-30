package com.arcadiaeditions.catalog.inventory.service.impl

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.catalog.inventory.events.*
import com.arcadiaeditions.catalog.inventory.repository.jpa.*
import com.arcadiaeditions.catalog.inventory.service.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.catalog.inventory.service.impl.mappers.*
import com.arcadiaeditions.common.DomainRejectionException
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
    // X-06/G-FT-04: wired now so the constructor is stable; used once receiveStock/adjustStock are implemented.
    private val clock: Clock,
) : InventoryBackofficeService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val inventoryBackofficeServiceMapper: InventoryBackofficeServiceMapper =
        InventoryBackofficeServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    /**
     * Convert a plain Kotlin Int to the boxed java.lang.Integer type.
     * Generated entities/DTOs use boxed Integer for numeric fields (so they can be null),
     * which requires explicit conversion when working with Kotlin arithmetic results.
     */
    private fun Int.toBoxed(): Integer = this as Integer

    /**
     * G-CI-06: creates an inventory position with initial quantities. Sets availableQuantity to
     * initialQuantity, and initializes reservedQuantity and soldQuantity to 0. Rejects duplicate
     * SKU with a domain rejection (409).
     */
    override fun createInventoryPosition(input: CreateInventoryPositionInput): InventoryPosition {

        log.debug("[CRUD] Request to save InventoryPosition: {}", input)

        // Check for duplicate SKU
        if (inventoryPositionRepository.findBySku(input.sku) != null) {
            throw DomainRejectionException("InventoryPosition already exists for sku: ${input.sku}")
        }

        var inventoryPosition = inventoryBackofficeServiceMapper.update(InventoryPosition(), input)
        // G-CI-06: Set initial quantities from initialQuantity
        inventoryPosition.availableQuantity = input.initialQuantity
        inventoryPosition.reservedQuantity = 0.toBoxed()
        inventoryPosition.soldQuantity = 0.toBoxed()

        inventoryPosition = inventoryPositionRepository.save(inventoryPosition)
        // TODO: may need to reload the entity to fetch relationships 'mapped by id'
        return inventoryPosition
    }

    /**
     * G-CI-07: receives stock for a sku, increasing available quantity and emitting a StockReceived
     * event with all required fields (quantity, reason, reference, timestamp). Throws
     * NoSuchElementException if the sku does not exist.
     */
    override fun receiveStock(input: ReceiveStockInput): InventoryPosition {

        log.debug("Request receiveStock: {}", input)

        // Load by sku; throw 404 if not found
        val inventoryPosition = inventoryPositionRepository.findBySku(input.sku)
            ?: throw NoSuchElementException("InventoryPosition not found for sku: ${input.sku}")

        // Apply quantity arithmetic: available += quantity
        val quantity = input.quantity!!.toInt()
        inventoryPosition.availableQuantity = (inventoryPosition.availableQuantity!!.toInt() + quantity).toBoxed()

        // Save the updated position
        val saved = inventoryPositionRepository.save(inventoryPosition)

        // Emit the event with all required fields from the input + clock timestamp
        val now = Instant.now(clock)
        eventPublisher.onStockReceived(
            eventsMapper.asStockReceived(input.sku, input.quantity, saved.availableQuantity, saved.reservedQuantity, saved.soldQuantity, input.reason, input.reference, now)
        )

        return saved
    }

    /**
     * G-CI-07: adjusts stock for a sku by a delta quantity (can be negative), emitting a
     * StockAdjusted event. Throws NoSuchElementException if sku not found, and
     * DomainRejectionException if the resulting availableQuantity would be negative (Q-14).
     */
    override fun adjustStock(input: AdjustStockInput): InventoryPosition {

        log.debug("Request adjustStock: {}", input)

        // Load by sku; throw 404 if not found
        val inventoryPosition = inventoryPositionRepository.findBySku(input.sku)
            ?: throw NoSuchElementException("InventoryPosition not found for sku: ${input.sku}")

        // Apply quantity arithmetic: available += quantityDelta
        val currentAvailable = inventoryPosition.availableQuantity!!.toInt()
        val delta = input.quantityDelta!!.toInt()
        val resultingAvailable = currentAvailable + delta

        // Reject negative result (Q-14)
        if (resultingAvailable < 0) {
            throw DomainRejectionException("Adjustment would result in negative availableQuantity ($currentAvailable + $delta = $resultingAvailable) for sku: ${input.sku}")
        }

        inventoryPosition.availableQuantity = resultingAvailable.toBoxed()

        // Save the updated position
        val saved = inventoryPositionRepository.save(inventoryPosition)

        // Emit the event with all required fields from the input + clock timestamp
        val now = Instant.now(clock)
        eventPublisher.onStockAdjusted(
            eventsMapper.asStockAdjusted(input.sku, input.quantityDelta, saved.availableQuantity, saved.reservedQuantity, saved.soldQuantity, input.reason, input.reference, now)
        )

        return saved
    }
}
