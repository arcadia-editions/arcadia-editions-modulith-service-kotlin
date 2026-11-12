package com.arcadiaeditions.fulfillment.shipping

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
interface FulfillmentRepository : JpaRepository<Fulfillment, Long> {}
