package com.arcadiaeditions.fulfillment.shipping.repository.jpa

import com.arcadiaeditions.fulfillment.shipping.domain.*
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository

/** Spring Data JPA repository for the Fulfillment entity. */
@Suppress("unused")
@Repository
@org.jmolecules.ddd.annotation.Repository
@org.jmolecules.architecture.layered.InfrastructureLayer
interface FulfillmentRepository : JpaRepository<Fulfillment, Long> {
    fun findByFulfillmentId(fulfillmentId: String): Fulfillment?

    fun findByOrderId(orderId: String): Fulfillment?
}
