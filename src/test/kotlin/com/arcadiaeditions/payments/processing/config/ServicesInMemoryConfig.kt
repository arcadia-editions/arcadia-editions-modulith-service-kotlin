package com.arcadiaeditions.payments.processing.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.*
import com.arcadiaeditions.payments.processing.service.*
import com.arcadiaeditions.payments.processing.service.impl.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()
    protected val paymentsProcessingService = PaymentsProcessingServiceImpl(paymentRepository(), eventPublisher)

    @Bean
    open fun <T : PaymentsProcessingService> paymentsProcessingService(): T {
        return paymentsProcessingService as T
    }

    companion object {
        var _payments: List<Payment>? = null
    }

    fun reloadTestData() {

        val testDataLoader = TestDataLoader(listOf(Payment::class.java))
        val payments = _payments ?: testDataLoader.loadCollectionTestDataAsObjects(Payment::class.java)
        paymentRepository().deleteAll()
        paymentRepository().saveAll(payments)
        eventPublisher.getEvents().clear()
    }

    @Bean
    fun eventPublisher(): InMemoryEventPublisher {
        return eventPublisher
    }
}
