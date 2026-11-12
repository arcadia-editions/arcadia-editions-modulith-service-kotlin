package com.arcadiaeditions.payments.processing.application.port.driven.jpa

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
@org.jmolecules.architecture.hexagonal.SecondaryPort
interface PaymentRepository : JpaRepository<Payment, Long> {}
