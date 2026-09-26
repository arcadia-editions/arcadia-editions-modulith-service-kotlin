package com.arcadiaeditions.fulfillment.shipping.service.dtos

import com.arcadiaeditions.fulfillment.shipping.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** ScheduleFulfillmentInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class ScheduleFulfillmentInput(
    @NotNull val orderId: String,
    @NotNull val customerId: String,
    @NotNull val shippingAddress: ShippingAddressInput,
    @NotNull val items: MutableList<FulfillmentItemInput> = mutableListOf(),
) : Serializable {

    fun addItems(items: FulfillmentItemInput): ScheduleFulfillmentInput {
        this.items += items
        return this
    }
}
