package com.arcadiaeditions.payments.processing.service.impl

import com.arcadiaeditions.payments.processing.config.*
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.repository.jpa.*
import com.arcadiaeditions.payments.processing.repository.jpa.inmemory.*
import com.arcadiaeditions.payments.processing.service.*
import com.arcadiaeditions.payments.processing.service.dtos.*
import com.arcadiaeditions.payments.processing.service.impl.mappers.*
import java.time.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for PaymentsProcessingService. */
class PaymentsProcessingServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val paymentsProcessingService: PaymentsProcessingService = context.paymentsProcessingService()

    val paymentRepository: PaymentRepositoryInMemory = context.paymentRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun authorizePaymentTest() { // TODO: implement this test
    }

    @Test
    fun retryPaymentTest() { // TODO: implement this test
    }

    @Test
    fun capturePaymentTest() { // TODO: implement this test
    }

    @Test
    fun voidPaymentTest() { // TODO: implement this test
    }
}
