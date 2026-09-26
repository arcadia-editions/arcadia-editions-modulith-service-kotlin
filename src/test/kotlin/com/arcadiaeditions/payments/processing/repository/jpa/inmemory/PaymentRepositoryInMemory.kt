package com.arcadiaeditions.payments.processing.repository.jpa.inmemory

import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.repository.jpa.PaymentRepository
import java.math.*
import java.time.*
import java.util.*

class PaymentRepositoryInMemory : InMemoryJpaRepository<Payment, Long>(), PaymentRepository {}
