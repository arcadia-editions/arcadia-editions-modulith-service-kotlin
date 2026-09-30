package com.arcadiaeditions.catalog.products.service.impl

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.domain.events.*
import com.arcadiaeditions.catalog.products.events.*
import com.arcadiaeditions.catalog.products.repository.jpa.*
import com.arcadiaeditions.catalog.products.service.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import com.arcadiaeditions.catalog.products.service.impl.mappers.*
import com.arcadiaeditions.common.DomainRejectionException
import java.math.*
import java.time.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Service Implementation for managing [Product]. */
@Service
@Transactional
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
open class ProductCatalogServiceImpl(
    private val productRepository: ProductRepository,
    private val eventPublisher: EventPublisher,
    // X-06: wired for consistency with the other services' timestamps.
    private val clock: Clock,
) : ProductCatalogService {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val productCatalogServiceMapper: ProductCatalogServiceMapper = ProductCatalogServiceMapper.INSTANCE

    private val eventsMapper: EventsMapper = EventsMapper.INSTANCE

    /**
     * G-CP-02: creates a new product with sku uniqueness check.
     * Rejects duplicate SKU with a domain rejection (409).
     */
    override fun createProduct(input: CreateProductInput): Product {

        log.debug("[CRUD] Request to save Product: {}", input)

        // Check for duplicate SKU
        if (productRepository.findBySku(input.sku) != null) {
            throw DomainRejectionException("Product already exists for sku: ${input.sku}")
        }

        var product = productCatalogServiceMapper.update(Product(), input)
        product = productRepository.save(product)
        // TODO: may need to reload the entity to fetch relationships 'mapped by id'
        return product
    }

    override fun updateProduct(id: Long, input: UpdateProductInput): Product {

        log.debug("Request updateProduct: {} {}", id, input)

        return productRepository
            .findByIdOrNull(id)
            ?.let { existingProduct ->
                ProductTransitions.ensureCanUpdateProduct(existingProduct)
                productCatalogServiceMapper.update(existingProduct, input)
                existingProduct.status = ProductStatus.DRAFT
                existingProduct
            }
            ?.let { productRepository.save(it) } ?: throw NoSuchElementException("Product not found with id: $id")
    }

    /**
     * G-CP-01: publishes a product and emits ProductPublished with productId (String from entity id)
     * and publishedAt timestamp.
     */
    override fun publishProduct(id: Long): Product {

        log.debug("Request publishProduct: {}", id)

        val existingProduct =
            productRepository.findByIdOrNull(id) ?: throw NoSuchElementException("Product not found with id: $id")
        ProductTransitions.ensureCanPublishProduct(existingProduct)
        existingProduct.status = ProductStatus.PUBLISHED
        val product = productRepository.save(existingProduct)
        // emit events
        val now = Instant.now(clock)
        eventPublisher.onProductPublished(eventsMapper.asProductPublished(product, now))
        return product
    }

    /**
     * G-CP-01: retires a product and emits ProductRetired with productId (String from entity id)
     * and retiredAt timestamp.
     */
    override fun retireProduct(id: Long): Product {

        log.debug("Request retireProduct: {}", id)

        val existingProduct =
            productRepository.findByIdOrNull(id) ?: throw NoSuchElementException("Product not found with id: $id")
        ProductTransitions.ensureCanRetireProduct(existingProduct)
        existingProduct.status = ProductStatus.RETIRED
        val product = productRepository.save(existingProduct)
        // emit events
        val now = Instant.now(clock)
        eventPublisher.onProductRetired(eventsMapper.asProductRetired(product, now))
        return product
    }
}
