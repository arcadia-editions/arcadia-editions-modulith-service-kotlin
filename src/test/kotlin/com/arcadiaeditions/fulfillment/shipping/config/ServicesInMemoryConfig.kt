package com.arcadiaeditions.fulfillment.shipping.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.events.*
import com.arcadiaeditions.fulfillment.shipping.adapters.stub.StubFulfillmentCarrier
import com.arcadiaeditions.fulfillment.shipping.service.*
import com.arcadiaeditions.fulfillment.shipping.service.impl.*
import com.arcadiaeditions.fulfillment.shipping.service.ports.FulfillmentCarrier
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

    // Deterministic stub port (Q-09); tests can force outcomes via its `forcedOutcome` property.
    protected val fulfillmentCarrier = StubFulfillmentCarrier()

    protected val fulfillmentShippingService =
        FulfillmentShippingServiceImpl(fulfillmentRepository(), eventPublisher, clock, fulfillmentCarrier)

    @Bean
    open fun <T : FulfillmentShippingService> fulfillmentShippingService(): T {
        return fulfillmentShippingService as T
    }

    @Bean open fun clock(): Clock = clock

    @Bean open fun fulfillmentCarrier(): FulfillmentCarrier = fulfillmentCarrier

    companion object {
        var _fulfillments: List<Fulfillment>? = null
    }

    fun reloadTestData() {

        val testDataLoader =
            TestDataLoader(listOf(Fulfillment::class.java, ShippingAddress::class.java, FulfillmentItem::class.java))
        val fulfillments = _fulfillments ?: testDataLoader.loadCollectionTestDataAsObjects(Fulfillment::class.java)
        fulfillmentRepository().deleteAll()
        fulfillmentRepository().saveAll(fulfillments)
        eventPublisher.getEvents().clear()
    }

    @Bean
    fun eventPublisher(): InMemoryEventPublisher {
        return eventPublisher
    }
}
