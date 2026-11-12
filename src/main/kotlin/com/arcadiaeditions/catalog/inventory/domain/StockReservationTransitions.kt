package com.arcadiaeditions.catalog.inventory.domain
object StockReservationTransitions {

    fun ensureCanReleaseStock(entity: StockReservation) {
        require("releaseStock", entity, ReservationStatus.RESERVED, ReservationStatus.CONFIRMED)
    }

    fun ensureCanConfirmStockReservation(entity: StockReservation) {
        require("confirmStockReservation", entity, ReservationStatus.RESERVED)
    }

    private fun require(command: String, entity: StockReservation, vararg allowed: ReservationStatus) {
        val current = entity.status
        if (current != null && allowed.any { it == current }) {
            return
        }
        throw InvalidStateTransitionException(
            entityType = entity::class.simpleName ?: "StockReservation",
            entityId = entity.id,
            naturalId = null,
            operation = command,
            currentState = current,
            allowedStates = allowed.toList(),
        )
    }

    class InvalidStateTransitionException(
        val entityType: String,
        val entityId: Any?,
        val naturalId: Any?,
        val operation: String,
        val currentState: Any?,
        val allowedStates: List<*>,
    ) :
        IllegalStateException(
            "Invalid state transition for $entityType (${naturalId ?: "id=$entityId"}): " +
                "operation '$operation' not allowed from state $currentState, allowed states: $allowedStates"
        )
}
