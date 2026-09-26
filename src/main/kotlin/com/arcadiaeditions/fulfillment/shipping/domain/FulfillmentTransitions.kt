package com.arcadiaeditions.fulfillment.shipping.domain
object FulfillmentTransitions {

    fun ensureCanMarkReadyToShip(entity: Fulfillment) {
        require("markReadyToShip", entity, FulfillmentStatus.SCHEDULED)
    }

    private fun require(command: String, entity: Fulfillment, vararg allowed: FulfillmentStatus) {
        val current = entity.status
        if (current != null && allowed.any { it == current }) {
            return
        }
        throw InvalidStateTransitionException(
            entityType = entity::class.simpleName ?: "Fulfillment",
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
