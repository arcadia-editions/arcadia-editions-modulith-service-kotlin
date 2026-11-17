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
        val toSave = fulfillmentShippingServiceMapper.update(Fulfillment(), input)
        toSave.fulfillmentId = "FUL-" + input.orderId.removePrefix("ORD-")
        if (result.scheduled) {
            toSave.status = FulfillmentStatus.SCHEDULED
            toSave.scheduledAt = Instant.now(clock)
        } else {
            // A-01 (P-02): FAILED is set here, as the outcome of FulfillmentFailed
            toSave.status = FulfillmentStatus.FAILED
            toSave.failedAt = Instant.now(clock)
            toSave.failureReason = result.reason
        }
        val fulfillment = fulfillmentRepository.save(toSave)
        // emit events
        if (result.scheduled) {
            eventPublisher.onFulfillmentScheduled(eventsMapper.asFulfillmentScheduled(fulfillment))
        } else {
            eventPublisher.onFulfillmentFailed(eventsMapper.asFulfillmentFailed(fulfillment))
        }
        return fulfillment
    }

    override fun markReadyToShip(id: Long): Fulfillment {

        log.debug("Request markReadyToShip: {}", id)

        val existingFulfillment =
            fulfillmentRepository.findByIdOrNull(id)
                ?: throw NoSuchElementException("Fulfillment not found with id: $id")
        FulfillmentTransitions.ensureCanMarkReadyToShip(existingFulfillment)
        // the warehouse ships only after capture: refused (no transition, no event) until paymentCapturedAt is set
        if (existingFulfillment.paymentCapturedAt == null) {
            throw FulfillmentTransitions.InvalidStateTransitionException(
                entityType = "Fulfillment",
                entityId = existingFulfillment.id,
                naturalId = null,
                operation = "markReadyToShip",
                currentState = existingFulfillment.status,
                allowedStates = listOf(FulfillmentStatus.SCHEDULED),
            )
        }
        existingFulfillment.status = FulfillmentStatus.READY_TO_SHIP
        val fulfillment = fulfillmentRepository.save(existingFulfillment)
        // emit events
        eventPublisher.onFulfillmentReadyToShip(eventsMapper.asFulfillmentReadyToShip(fulfillment))
        return fulfillment
    }

    override fun recordPaymentCaptured(input: RecordPaymentCapturedInput): Fulfillment? {

        log.debug("Request recordPaymentCaptured: {}", input)

        // no Fulfillment: no-op
        val existingFulfillment = fulfillmentRepository.findByOrderId(input.orderId) ?: return null
        // not SCHEDULED, or already recorded (redelivery): no-op, no event
        if (existingFulfillment.status != FulfillmentStatus.SCHEDULED || existingFulfillment.paymentCapturedAt != null) {
            return existingFulfillment
        }
        existingFulfillment.paymentCapturedAt = input.capturedAt
        return fulfillmentRepository.save(existingFulfillment)
    }

    override fun cancelFulfillment(input: CancelFulfillmentInput): Fulfillment? {

        log.debug("Request cancelFulfillment: {}", input)

        // no Fulfillment: no-op
        val existingFulfillment = fulfillmentRepository.findByOrderId(input.orderId) ?: return null
        // CANCELLED (redelivery) or FAILED: no-op, no event
        if (
            existingFulfillment.status != FulfillmentStatus.SCHEDULED &&
                existingFulfillment.status != FulfillmentStatus.READY_TO_SHIP
        ) {
            return existingFulfillment
        }
        FulfillmentTransitions.ensureCanCancelFulfillment(existingFulfillment)
        existingFulfillment.status = FulfillmentStatus.CANCELLED
        existingFulfillment.cancelledAt = Instant.now(clock)
        // A-11: the reason of OrderCancelled
        existingFulfillment.cancellationReason = input.reason
        val fulfillment = fulfillmentRepository.save(existingFulfillment)
        // emit events
        eventPublisher.onFulfillmentCancelled(eventsMapper.asFulfillmentCancelled(fulfillment))
        return fulfillment
    }
}
