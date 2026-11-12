package com.arcadiaeditions.fulfillment.shipping

/** Outbound port to the warehouse: asks whether the fulfillment of a confirmed order can be arranged. */
@org.jspecify.annotations.NullMarked
@org.jmolecules.architecture.hexagonal.SecondaryPort
interface Warehouse {

    /** Never throws for a warehouse outcome: see [WarehouseResult]. */
    fun schedule(request: WarehouseRequest): WarehouseResult
}

data class WarehouseRequest(val orderId: String, val items: List<WarehouseItem>)

data class WarehouseItem(val sku: String, val quantity: Int)

data class WarehouseResult(
    val scheduled: Boolean,
    /** Reason code when not [scheduled] (A-06: warehouse_damage, fulfillment_failed, ...). */
    val reason: String? = null,
) {
    companion object {
        fun scheduled() = WarehouseResult(true)

        fun refused(reason: String) = WarehouseResult(false, reason)
    }
}
