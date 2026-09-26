package com.arcadiaeditions.fulfillment.shipping.service.impl

import com.arcadiaeditions.fulfillment.shipping.config.*
import com.arcadiaeditions.fulfillment.shipping.domain.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.*
import com.arcadiaeditions.fulfillment.shipping.repository.jpa.inmemory.*
import com.arcadiaeditions.fulfillment.shipping.service.*
import com.arcadiaeditions.fulfillment.shipping.service.dtos.*
import com.arcadiaeditions.fulfillment.shipping.service.impl.mappers.*
import java.time.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for FulfillmentShippingService. */
class FulfillmentShippingServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val fulfillmentShippingService: FulfillmentShippingService = context.fulfillmentShippingService()

    val fulfillmentRepository: FulfillmentRepositoryInMemory = context.fulfillmentRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun scheduleFulfillmentTest() { // TODO: implement this test
    }

    @Test
    fun markReadyToShipTest() { // TODO: implement this test
    }
}
