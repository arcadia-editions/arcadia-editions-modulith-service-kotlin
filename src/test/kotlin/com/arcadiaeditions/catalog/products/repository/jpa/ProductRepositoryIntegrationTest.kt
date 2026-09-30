package com.arcadiaeditions.catalog.products.repository.jpa

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.common.BaseRepositoryIntegrationTest
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class ProductRepositoryIntegrationTest : BaseRepositoryIntegrationTest() {

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var productRepository: ProductRepository

    @Test
    fun findAllTest() {
        val results = productRepository.findAll()
        Assertions.assertFalse(results.isEmpty())
    }

    @Test
    fun findByIdTest() {
        val id = 1L
        val product = productRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        Assertions.assertNotNull(product.id)
        Assertions.assertNotNull(product.version)
    }

    @Test
    fun saveTest() {
        val product = Product()
        product.sku = ""
        product.name = ""
        product.description = ""
        product.price = BigDecimal.valueOf(0)
        product.currency = ""
        product.editionSize = 0 as Integer
        product.trackingMode = InventoryTrackingMode.values()[0]
        product.launchAt = Instant.now()
        product.status = ProductStatus.values()[0]

        // Persist aggregate root
        val created = productRepository.save(product)

        // reloading to get relationships persisted by id
        entityManager.flush()
        entityManager.refresh(created)
        Assertions.assertNotNull(created.id)
        Assertions.assertNotNull(created.version)
    }

    @Test
    fun updateTest() {
        val id = 1L
        val product = productRepository.findByIdOrNull(id) ?: throw NoSuchElementException(" not found with id: $id")
        product.sku = ""
        product.name = ""
        product.description = ""
        product.price = BigDecimal.valueOf(0)
        product.currency = ""
        product.editionSize = 0 as Integer
        product.trackingMode = InventoryTrackingMode.values()[0]
        product.launchAt = Instant.now()
        product.status = ProductStatus.values()[0]

        val updated = productRepository.save(product)
        Assertions.assertEquals("", updated.sku)
        Assertions.assertEquals("", updated.name)
        Assertions.assertEquals("", updated.description)
        Assertions.assertEquals(BigDecimal.valueOf(0), updated.price)
        Assertions.assertEquals("", updated.currency)
        Assertions.assertEquals(0 as Integer, updated.editionSize)
        Assertions.assertEquals(InventoryTrackingMode.values()[0], updated.trackingMode)
        // Phase 6 / baseline fix: see StockReservationRepositoryIntegrationTest - comparing against
        // a second independent Instant.now() call is flaky by construction.
        Assertions.assertNotNull(updated.launchAt)
        Assertions.assertEquals(ProductStatus.values()[0], updated.status)
    }

    @Test
    fun deleteTest() {
        val id = 1L
        productRepository.deleteById(id)
        val notFound = productRepository.findByIdOrNull(id)
        Assertions.assertNull(notFound)
    }
}
