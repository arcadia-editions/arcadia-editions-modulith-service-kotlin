package com.arcadiaeditions.fulfillment.shipping.inmemory

import com.arcadiaeditions.fulfillment.shipping.FulfillmentRepository
import com.arcadiaeditions.fulfillment.shipping.domain.*
import java.math.*
import java.time.*
import java.util.*

class FulfillmentRepositoryInMemory : InMemoryJpaRepository<Fulfillment, Long>(), FulfillmentRepository {

    override fun findByOrderId(orderId: String): Fulfillment? = findAll().firstOrNull { it.orderId == orderId }
}
