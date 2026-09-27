package com.arcadiaeditions.catalog.products.service.dtos

import com.arcadiaeditions.catalog.products.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ProductPublishedResponse. */
@org.jmolecules.ddd.annotation.ValueObject
data class ProductPublishedResponse(val productId: String?, val sku: String?, val publishedAt: Instant?) :
    Serializable {}
