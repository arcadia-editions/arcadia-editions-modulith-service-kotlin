package com.arcadiaeditions.fulfillment.shipping.service.impl

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.events.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.*
import com.arcadiaeditions.fulfillment.shipping.service.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
import com.arcadiaeditions.fulfillment.shipping.service.impl.mappers.*
import com.arcadiaeditions.fulfillment.shipping.service.ports.FulfillmentCarrier
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleOutcome
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleRequest
import com.arcadiaeditions.fulfillment.shipping.service.ports.ScheduleRequestItem
import java.math.*
import java.time.*
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Fulfillment]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class FulfillmentShippingServiceImpl(
    private val fulfillmentRepository: FulfillmentRepository,
    private val eventPublisher: EventPublisher,
    // X-06/G-FT-04: wired now so the constructor is stable; used once scheduleFulfillment is implemented.
    private val clock: Clock,
    private val fulfillmentCarrier: FulfillmentCarrier,
) : FulfillmentShippingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val fulfillmentShippingServiceMapper: FulfillmentShippingServiceMapper =
        FulfillmentShippingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    /**
     * G-FS-02/M-11: `scheduleFulfillment` is a create (`@transition(to: SCHEDULED)`). Idempotency is
     * by `orderId` (unique on [Fulfillment]): a fulfillment already exists for this order means
     * `OrderConfirmed` was redelivered and this command already ran - return the existing result
     * without calling the carrier or emitting again. Otherwise generate `fulfillmentId`, store the
     * shipping address and items, call [FulfillmentCarrier.schedule] and emit exactly one of
     * FulfillmentScheduled/FulfillmentFailed depending on the outcome.
     */
    override fun scheduleFulfillment(input: ScheduleFulfillmentInput): Fulfillment {

        log.debug("Request scheduleFulfillment: {}", input)

        val existing = fulfillmentRepository.findByOrderId(input.orderId)
        if (existing != null) {
            log.debug("Idempotent redelivery of scheduleFulfillment for order {}", input.orderId)
            return existing
        }

        val fulfillment = fulfillmentShippingServiceMapper.update(Fulfillment(), input)
        fulfillment.fulfillmentId = "FUL-${UUID.randomUUID()}"
        fulfillment.status = FulfillmentStatus.SCHEDULED

        val now = Instant.now(clock)
        val result =
            fulfillmentCarrier.schedule(
                ScheduleRequest(
                    orderId = input.orderId,
                    customerId = input.customerId,
                    postalCode = input.shippingAddress.postalCode,
                    items = input.items.map { ScheduleRequestItem(sku = it.sku, quantity = it.quantity.toInt()) },
                )
            )

        return when (result.outcome) {
            ScheduleOutcome.SCHEDULED -> {
                fulfillment.scheduledAt = now
                fulfillment.carrierCode = result.carrierCode
                fulfillment.trackingNumber = result.trackingNumber
                val saved = fulfillmentRepository.save(fulfillment)
                eventPublisher.onFulfillmentScheduled(eventsMapper.asFulfillmentScheduled(saved))
                saved
            }
            ScheduleOutcome.FAILED -> {
                fulfillment.status = FulfillmentStatus.FAILED
                fulfillment.failedAt = now
                fulfillment.failureReason = result.failureReason
                val saved = fulfillmentRepository.save(fulfillment)
                eventPublisher.onFulfillmentFailed(eventsMapper.asFulfillmentFailed(saved))
                saved
            }
        }
    }

    override fun markReadyToShip(id: Long): Fulfillment {

        log.debug("Request markReadyToShip: {}", id)

        val existingFulfillment =
            fulfillmentRepository.findByIdOrNull(id)
                ?: throw NoSuchElementException("Fulfillment not found with id: $id")
        FulfillmentTransitions.ensureCanMarkReadyToShip(existingFulfillment)
        existingFulfillment.status = FulfillmentStatus.READY_TO_SHIP
        existingFulfillment.readyAt = Instant.now(clock)
        val fulfillment = fulfillmentRepository.save(existingFulfillment)
        // emit events
        eventPublisher.onFulfillmentReadyToShip(eventsMapper.asFulfillmentReadyToShip(fulfillment))
        return fulfillment
    }
}
