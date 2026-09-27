package com.arcadiaeditions.catalog.inventory.service.dtos

import com.arcadiaeditions.catalog.inventory.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** StockUnavailableResult. */
@org.jmolecules.ddd.annotation.ValueObject
data class StockUnavailableResult(
    val orderId: String?,
    val unavailableItems: MutableList<StockUnavailableLine> = mutableListOf(),
) : Serializable {

    fun addUnavailableItems(unavailableItems: StockUnavailableLine): StockUnavailableResult {
        this.unavailableItems += unavailableItems
        return this
    }
}
