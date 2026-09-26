package com.arcadiaeditions.payments.processing.repository.jpa

import com.arcadiaeditions.payments.processing.domain.*
import java.math.*
import java.time.*
import java.util.*
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository

/** Spring Data JPA repository for the Payment entity. */
@Suppress("unused")
@Repository
@org.jmolecules.ddd.annotation.Repository
@org.jmolecules.architecture.layered.InfrastructureLayer
interface PaymentRepository : JpaRepository<Payment, Long> {}
