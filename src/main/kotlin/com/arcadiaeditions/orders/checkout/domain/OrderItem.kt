package com.arcadiaeditions.orders.checkout.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
// @Embeddable // json embedded
@org.jmolecules.ddd.annotation.ValueObject
data class OrderItem(
    @NotNull @Column(name = "sku", nullable = false) var sku: String? = null,
    @NotNull @Min(1) @Column(name = "quantity", nullable = false) var quantity: Integer? = null,
    @NotNull @Column(name = "unit_price", nullable = false) var unitPrice: BigDecimal? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    override fun toString(): String {
        return this::class.java.name + "@" + Integer.toHexString(hashCode())
    }
}
