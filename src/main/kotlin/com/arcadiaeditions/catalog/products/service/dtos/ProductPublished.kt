package com.arcadiaeditions.catalog.products.service.dtos

import com.arcadiaeditions.catalog.products.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ProductPublished. */
@org.jmolecules.ddd.annotation.ValueObject
data class ProductPublished(val productId: String?, val sku: String?, val publishedAt: Instant?) : Serializable {}
