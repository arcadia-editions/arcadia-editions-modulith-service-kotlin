package com.arcadiaeditions.fulfillment.shipping.repository.jpa.inmemory

import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.FulfillmentRepository
import java.math.*
import java.time.*
import java.util.*

class FulfillmentRepositoryInMemory : InMemoryJpaRepository<Fulfillment, Long>(), FulfillmentRepository {}
