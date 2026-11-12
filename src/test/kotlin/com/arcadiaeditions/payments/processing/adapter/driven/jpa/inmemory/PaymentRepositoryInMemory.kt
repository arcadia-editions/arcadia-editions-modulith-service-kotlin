package com.arcadiaeditions.payments.processing.adapter.driven.jpa.inmemory

import com.arcadiaeditions.payments.processing.application.port.driven.jpa.PaymentRepository
import com.arcadiaeditions.payments.processing.domain.*
import java.math.*
import java.time.*
import java.util.*

class PaymentRepositoryInMemory : InMemoryJpaRepository<Payment, Long>(), PaymentRepository {}
