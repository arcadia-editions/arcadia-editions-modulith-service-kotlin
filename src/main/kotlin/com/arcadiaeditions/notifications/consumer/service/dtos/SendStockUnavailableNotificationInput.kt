package com.arcadiaeditions.notifications.consumer.service.dtos

import com.arcadiaeditions.notifications.consumer.domain.*
import jakarta.validation.constraints.*
import java.io.Serializable
import java.math.*
import java.time.*

/** SendStockUnavailableNotificationInput. */
@org.jmolecules.ddd.annotation.ValueObject
data class SendStockUnavailableNotificationInput(
    @NotNull val orderId: String,
    @NotNull val customerId: String,
    @NotNull val customerEmail: String,
    @NotNull @Size(min = 1) val unavailableItems: MutableList<StockUnavailableItemInput> = mutableListOf(),
) : Serializable {

    fun addUnavailableItems(unavailableItems: StockUnavailableItemInput): SendStockUnavailableNotificationInput {
        this.unavailableItems += unavailableItems
        return this
    }
}
