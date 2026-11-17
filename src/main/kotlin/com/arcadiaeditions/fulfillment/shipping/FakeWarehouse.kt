package com.arcadiaeditions.fulfillment.shipping

import org.springframework.stereotype.Component

/**
 * Deterministic stand-in for the warehouse: schedules every request unless [refusalReason] is set (tests set it to
 * simulate a refusal). A real warehouse adapter is a separate task.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["arcadia.fake-adapters.enabled"], havingValue = "true")
@org.jmolecules.architecture.hexagonal.SecondaryAdapter
class FakeWarehouse : Warehouse {

    /** When not null, every request is refused with this reason. */
    @Volatile var refusalReason: String? = null

    override fun schedule(request: WarehouseRequest): WarehouseResult =
        refusalReason?.let { WarehouseResult.refused(it) } ?: WarehouseResult.scheduled()
}
