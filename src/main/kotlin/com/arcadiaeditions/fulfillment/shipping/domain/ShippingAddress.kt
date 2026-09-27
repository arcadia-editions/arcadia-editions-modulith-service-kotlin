package com.arcadiaeditions.fulfillment.shipping.domain

import jakarta.persistence.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
// @Embeddable // json embedded
@org.jmolecules.ddd.annotation.ValueObject
data class ShippingAddress(
    @NotNull @Column(name = "name", nullable = false) var name: String? = null,
    @NotNull @Column(name = "line1", nullable = false) var line1: String? = null,
    @Column(name = "line2") var line2: String? = null,
    @NotNull @Column(name = "city", nullable = false) var city: String? = null,
    @Column(name = "region") var region: String? = null,
    @NotNull @Column(name = "postal_code", nullable = false) var postalCode: String? = null,
    @NotNull @Column(name = "country", nullable = false) var country: String? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    override fun toString(): String {
        return this::class.java.name + "@" + Integer.toHexString(hashCode())
    }
}
