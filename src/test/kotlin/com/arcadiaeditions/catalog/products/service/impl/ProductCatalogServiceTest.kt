package com.arcadiaeditions.catalog.products.service.impl

import com.arcadiaeditions.catalog.products.config.*
import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.ProductPublished
import com.arcadiaeditions.catalog.products.domain.events.ProductRetired
import com.arcadiaeditions.catalog.products.events.InMemoryEventPublisher
import com.arcadiaeditions.catalog.products.repository.jpa.*
import com.arcadiaeditions.catalog.products.repository.jpa.inmemory.*
import com.arcadiaeditions.catalog.products.service.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.catalog.products.service.impl.mappers.*
import com.arcadiaeditions.common.DomainRejectionException
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull

private fun Int.i(): Integer = this as Integer

/** Acceptance Test for ProductCatalogService. */
class ProductCatalogServiceTest {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    val context = ServicesInMemoryConfig()
    val productCatalogService: ProductCatalogService = context.productCatalogService()

    val productRepository: ProductRepositoryInMemory = context.productRepository()

    val eventPublisher: InMemoryEventPublisher = context.eventPublisher()

    @BeforeEach
    fun setUp() {
        context.reloadTestData()
    }

    // --- createProduct ---------------------------------------------------------------

    @Test
    fun `createProduct saves the product with DRAFT status (happy path)`() {
        val input = CreateProductInput(
            sku = "AE-NEW-PROD",
            name = "New Product",
            description = "A new product",
            price = BigDecimal("99.99"),
            currency = "EUR",
            editionSize = 50.i(),
            trackingMode = InventoryTrackingMode.QUANTITY,
            launchAt = null,
        )

        val product = productCatalogService.createProduct(input)

        assertNotNull(product.id)
        assertEquals("AE-NEW-PROD", product.sku)
        assertEquals("New Product", product.name)
        assertEquals(ProductStatus.DRAFT, product.status)
        assertEquals(product, productRepository.findBySku("AE-NEW-PROD"))
    }

    @Test
    fun `createProduct rejects duplicate sku with 409`() {
        // AE-EDN-001 already exists in fixtures (id 1)
        val input = CreateProductInput(
            sku = "AE-EDN-001",
            name = "Duplicate",
            description = "",
            price = BigDecimal("50.00"),
            currency = "EUR",
            editionSize = 25.i(),
            trackingMode = InventoryTrackingMode.QUANTITY,
            launchAt = null,
        )

        val exception = assertThrows<DomainRejectionException> {
            productCatalogService.createProduct(input)
        }
        assertEquals("Product already exists for sku: AE-EDN-001", exception.message)
    }

    // --- updateProduct ---------------------------------------------------------------

    @Test
    fun updateProductTest() {
        val id: Long = 1L
        val input: UpdateProductInput =
            UpdateProductInput(
                name = "Updated Name",
                description = "Updated description",
                price = BigDecimal.valueOf(75.50),
                currency = "USD",
                launchAt = Instant.now(),
            )
        val product = productCatalogService.updateProduct(id, input)
        assertNotNull(product)
        assertEquals("Updated Name", product.name)
        assertTrue(productRepository.containsEntity(product))
    }

    // --- publishProduct ---------------------------------------------------------------

    @Test
    fun `publishProduct transitions to PUBLISHED and emits one ProductPublished event`() {
        val id = 151L // AE-EDN-004 from fixtures is DRAFT
        val product = productRepository.findByIdOrNull(id)!!
        assertEquals(ProductStatus.DRAFT, product.status)

        val result = productCatalogService.publishProduct(id)

        assertEquals(ProductStatus.PUBLISHED, result.status)
        assertNotNull(result.id)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as ProductPublished
        assertEquals(id.toString(), event.productId)
        assertEquals("AE-EDN-004", event.sku)
        assertEquals("Arcadia Second Edition", event.name)
        assertEquals(BigDecimal("59.99"), event.price)
        assertNotNull(event.publishedAt)
    }

    @Test
    fun `publishProduct throws 404 when product not found`() {
        val exception = assertThrows<NoSuchElementException> {
            productCatalogService.publishProduct(9999L)
        }
        assertTrue(exception.message!!.contains("9999"))
    }

    // --- retireProduct ---------------------------------------------------------------

    @Test
    fun `retireProduct transitions to RETIRED and emits one ProductRetired event`() {
        val id = 1L // AE-EDN-001 from fixtures is PUBLISHED
        val product = productRepository.findByIdOrNull(id)!!
        assertEquals(ProductStatus.PUBLISHED, product.status)

        val result = productCatalogService.retireProduct(id)

        assertEquals(ProductStatus.RETIRED, result.status)
        assertNotNull(result.id)

        assertEquals(1, eventPublisher.getEvents().size)
        val event = eventPublisher.getEvents().single() as ProductRetired
        assertEquals(id.toString(), event.productId)
        assertEquals("AE-EDN-001", event.sku)
        assertNotNull(event.retiredAt)
    }

    @Test
    fun `retireProduct throws 404 when product not found`() {
        val exception = assertThrows<NoSuchElementException> {
            productCatalogService.retireProduct(9999L)
        }
        assertTrue(exception.message!!.contains("9999"))
    }
}
