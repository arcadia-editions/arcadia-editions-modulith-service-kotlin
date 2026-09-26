package com.arcadiaeditions.catalog.products.domain.events

import com.arcadiaeditions.catalog.products.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.ProductRetired")
@org.jmolecules.event.annotation.DomainEvent
@org.jmolecules.architecture.layered.DomainLayer
data class ProductRetired(var productId: String? = null, var sku: String? = null, var retiredAt: Instant? = null) :
    Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
