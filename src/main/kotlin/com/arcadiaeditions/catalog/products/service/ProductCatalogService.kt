package com.arcadiaeditions.catalog.products.service

import com.arcadiaeditions.catalog.products.domain.*
import com.arcadiaeditions.catalog.products.service.dtos.*
import java.math.*
import java.time.*

/** Inbound Service Port for managing [Product]. */
@org.springframework.modulith.NamedInterface("ProductCatalogService")
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.layered.ApplicationLayer
interface ProductCatalogService {

    /**  */
    fun createProduct(input: CreateProductInput): Product

    /**  */
    fun updateProduct(id: Long, input: UpdateProductInput): Product

    /** With Events: [ProductPublished]. */
    fun publishProduct(id: Long): Product

    /** With Events: [ProductRetired]. */
    fun retireProduct(id: Long): Product
}
