package com.arcadiaeditions.fulfillment.shipping.service.impl

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.events.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.*
import com.arcadiaeditions.fulfillment.shipping.service.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
import com.arcadiaeditions.fulfillment.shipping.service.impl.mappers.*
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
@org.jmolecules.architecture.layered.ApplicationLayer
open class FulfillmentShippingServiceImpl(
    private val fulfillmentRepository: FulfillmentRepository,
    private val eventPublisher: EventPublisher,
) : FulfillmentShippingService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val fulfillmentShippingServiceMapper: FulfillmentShippingServiceMapper =
        FulfillmentShippingServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    override fun scheduleFulfillment(input: ScheduleFulfillmentInput): Fulfillment {

        log.debug("Request scheduleFulfillment: {}", input)

        val fulfillment = fulfillmentShippingServiceMapper.update(Fulfillment(), input)
        // TODO: implement this method
        // emit events
        eventPublisher.onFulfillmentScheduled(eventsMapper.asFulfillmentScheduled(fulfillment))
        eventPublisher.onFulfillmentFailed(eventsMapper.asFulfillmentFailed(fulfillment))
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
