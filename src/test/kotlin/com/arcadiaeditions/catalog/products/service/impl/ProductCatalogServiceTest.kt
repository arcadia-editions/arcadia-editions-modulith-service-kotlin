package com.arcadiaeditions.catalog.products.service.impl

import com.arcadiaeditions.catalog.products.config.*
import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.repository.jpa.*
import com.arcadiaeditions.catalog.products.repository.jpa.inmemory.*
import com.arcadiaeditions.catalog.products.service.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.catalog.products.service.impl.mappers.*
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Acceptance Test for ProductCatalogService. */
class ProductCatalogServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val productCatalogService: ProductCatalogService = context.productCatalogService()

    val productRepository: ProductRepositoryInMemory = context.productRepository()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    @Test
    fun createProductTest() {
        val input: CreateProductInput =
            CreateProductInput(
                sku = "",
                name = "",
                description = "",
                price = BigDecimal.valueOf(0),
                currency = "",
                editionSize = 0 as Integer,
                trackingMode = InventoryTrackingMode.values()[0],
                launchAt = Instant.now(),
            )
        val product = productCatalogService.createProduct(input)
        assertNotNull(product.id)
        assertTrue(productRepository.containsEntity(product))
    }

    @Test
    fun updateProductTest() {
        val id: Long = 1L
        val input: UpdateProductInput =
            UpdateProductInput(
                name = "",
                description = "",
                price = BigDecimal.valueOf(0),
                currency = "",
                launchAt = Instant.now(),
            )
        // assertTrue(productRepository.containsKey(id))
        val product = productCatalogService.updateProduct(id, input)
        assertNotNull(product)
        assertTrue(productRepository.containsEntity(product!!))
    }

    @Test
    fun publishProductTest() { // TODO: implement this test
    }

    @Test
    fun retireProductTest() { // TODO: implement this test
    }
}
