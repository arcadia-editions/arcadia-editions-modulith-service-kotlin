package com.arcadiaeditions.orders.checkout.repository.jpa

import com.arcadiaeditions.orders.checkout.domain.*
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository

/** Spring Data JPA repository for the Order entity. */
@Suppress("unused")
@Repository
@org.jmolecules.ddd.annotation.Repository
@org.jmolecules.architecture.layered.InfrastructureLayer
interface OrderRepository : JpaRepository<Order, Long> {}
