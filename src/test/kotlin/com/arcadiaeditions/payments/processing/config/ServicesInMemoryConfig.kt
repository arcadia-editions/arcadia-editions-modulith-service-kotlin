package com.arcadiaeditions.payments.processing.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.payments.processing.domain.*
import com.arcadiaeditions.payments.processing.domain.events.*
import com.arcadiaeditions.payments.processing.events.*
import com.arcadiaeditions.payments.processing.adapters.stub.StubPaymentProvider
import com.arcadiaeditions.payments.processing.service.*
import com.arcadiaeditions.payments.processing.service.impl.*
import com.arcadiaeditions.payments.processing.service.ports.PaymentProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()

    // X-06: fixed clock so tests can assert on exact timestamps.
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    // Deterministic stub port (Q-06); tests can force outcomes via its `forced*Outcome` properties.
    protected val paymentProvider = StubPaymentProvider()

    protected val paymentsProcessingService =
        PaymentsProcessingServiceImpl(paymentRepository(), eventPublisher, clock, paymentProvider)

    @Bean
    open fun <T : PaymentsProcessingService> paymentsProcessingService(): T {
        return paymentsProcessingService as T
    }

    @Bean open fun clock(): Clock = clock

    @Bean open fun paymentProvider(): PaymentProvider = paymentProvider

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
