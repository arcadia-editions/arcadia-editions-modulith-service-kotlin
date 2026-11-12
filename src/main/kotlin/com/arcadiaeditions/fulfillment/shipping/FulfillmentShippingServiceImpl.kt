package com.arcadiaeditions.fulfillment.shipping

import com.arcadiaeditions.fulfillment.shipping.*
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.dtos.*
import com.arcadiaeditions.fulfillment.shipping.mappers.*
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Fulfillment]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
open class FulfillmentShippingServiceImpl(
    private val fulfillmentRepository: FulfillmentRepository,
    private val eventPublisher: EventPublisher,
    private val warehouse: Warehouse,
    private val clock: Clock,
) : FulfillmentShippingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val fulfillmentShippingServiceMapper: FulfillmentShippingServiceMapper =
        FulfillmentShippingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun scheduleFulfillment(input: ScheduleFulfillmentInput): Fulfillment {

        log.debug("Request scheduleFulfillment: {}", input)

        // redelivery: the orderId already has a Fulfillment
        fulfillmentRepository.findByOrderId(input.orderId)?.let {
            return it
        }

        val result =
            warehouse.schedule(
                WarehouseRequest(input.orderId, input.items.map { WarehouseItem(it.sku, it.quantity.toInt()) })
            )
        // the refusal branch (FulfillmentFailed) is T-26
        check(result.scheduled) { "Warehouse refused fulfillment of ${input.orderId}: ${result.reason}" }

        val toSave = fulfillmentShippingServiceMapper.update(Fulfillment(), input)
        toSave.fulfillmentId = "FUL-" + input.orderId.removePrefix("ORD-")
        toSave.status = FulfillmentStatus.SCHEDULED
        toSave.scheduledAt = Instant.now(clock)
        val fulfillment = fulfillmentRepository.save(toSave)
        // emit events
        eventPublisher.onFulfillmentScheduled(eventsMapper.asFulfillmentScheduled(fulfillment))
        return fulfillment
    }

    override fun markReadyToShip(id: Long): Fulfillment {

        log.debug("Request markReadyToShip: {}", id)

        val existingFulfillment =
            fulfillmentRepository.findByIdOrNull(id)
                ?: throw NoSuchElementException("Fulfillment not found with id: $id")
        FulfillmentTransitions.ensureCanMarkReadyToShip(existingFulfillment)
        existingFulfillment.status = FulfillmentStatus.READY_TO_SHIP
        val fulfillment = fulfillmentRepository.save(existingFulfillment)
        // emit events
        eventPublisher.onFulfillmentReadyToShip(eventsMapper.asFulfillmentReadyToShip(fulfillment))
        return fulfillment
    }
}
