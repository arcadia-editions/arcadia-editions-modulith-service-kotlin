package com.arcadiaeditions.fulfillment.shipping.config

import com.arcadiaeditions.config.TestDataLoader
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.domain.events.*
import com.arcadiaeditions.fulfillment.shipping.events.*
import com.arcadiaeditions.fulfillment.shipping.service.*
import com.arcadiaeditions.fulfillment.shipping.service.impl.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/** Services InMemory Config. It can be used standalone or with @SpringBootTest. */
@Configuration
@Profile("in-memory")
open class ServicesInMemoryConfig : RepositoriesInMemoryConfig() {

    private val eventPublisher = InMemoryEventPublisher()
    protected val fulfillmentShippingService = FulfillmentShippingServiceImpl(fulfillmentRepository(), eventPublisher)

    @Bean
    open fun <T : FulfillmentShippingService> fulfillmentShippingService(): T {
        return fulfillmentShippingService as T
    }

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
