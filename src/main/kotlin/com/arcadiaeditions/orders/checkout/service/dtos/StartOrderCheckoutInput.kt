package com.arcadiaeditions.orders.checkout.service.dtos

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StartOrderCheckoutInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class StartOrderCheckoutInput(
    @NotNull val customerId: String,
    @NotNull @Size(max = 254) val customerEmail: String,
    @NotNull val paymentMethodId: String,
    @NotNull @Size(max = 3) val currency: String,
    @NotNull @Size(min = 1) val items: MutableList<OrderItemInput> = mutableListOf(),
    @NotNull val shippingAddress: ShippingAddressInput,
) : Serializable {

    fun addItems(items: OrderItemInput): StartOrderCheckoutInput {
        this.items += items
        return this
    }
}
