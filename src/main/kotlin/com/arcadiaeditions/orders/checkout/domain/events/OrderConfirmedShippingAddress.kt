package com.arcadiaeditions.orders.checkout.domain.events

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.OrderConfirmedShippingAddress")
data class OrderConfirmedShippingAddress(
    var name: String? = null,
    var line1: String? = null,
    var line2: String? = null,
    var city: String? = null,
    var region: String? = null,
    var postalCode: String? = null,
    var country: String? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
