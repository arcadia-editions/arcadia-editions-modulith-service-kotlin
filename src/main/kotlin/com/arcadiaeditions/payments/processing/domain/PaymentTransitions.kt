package com.arcadiaeditions.payments.processing.domain
object PaymentTransitions {

    fun ensureCanRetryPayment(entity: Payment) {
        require("retryPayment", entity, PaymentStatus.FAILED)
    }

    fun ensureCanCapturePayment(entity: Payment) {
        require("capturePayment", entity, PaymentStatus.AUTHORIZED)
    }

    fun ensureCanVoidPayment(entity: Payment) {
        require(
            "voidPayment",
            entity,
            PaymentStatus.AUTHORIZED,
            PaymentStatus.AUTHORIZATION_REQUESTED,
            PaymentStatus.FAILED,
        )
    }

    private fun require(command: String, entity: Payment, vararg allowed: PaymentStatus) {
        val current = entity.status
        if (current != null && allowed.any { it == current }) {
            return
        }
        throw InvalidStateTransitionException(
            entityType = entity::class.simpleName ?: "Payment",
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
