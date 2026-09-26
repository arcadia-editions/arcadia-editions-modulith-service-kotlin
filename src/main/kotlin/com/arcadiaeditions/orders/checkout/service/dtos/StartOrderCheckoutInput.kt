package com.arcadiaeditions.orders.checkout.service.dtos

import com.arcadiaeditions.orders.checkout.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StartOrderCheckoutInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class StartOrderCheckoutInput(@Size(min = 1) val items: MutableList<String> = mutableListOf()) : Serializable {

    fun addItems(items: String): StartOrderCheckoutInput {
        this.items += items
        return this
    }
}
