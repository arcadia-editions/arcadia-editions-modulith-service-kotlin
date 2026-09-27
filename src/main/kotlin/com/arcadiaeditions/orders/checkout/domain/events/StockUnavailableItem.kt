package com.arcadiaeditions.orders.checkout.domain.events

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*
import java.util.*

/**  */
@org.springframework.modulith.NamedInterface("events.StockUnavailableItem")
data class StockUnavailableItem(
    var sku: String? = null,
    var requestedQuantity: Integer? = null,
    var availableQuantity: Integer? = null,
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
