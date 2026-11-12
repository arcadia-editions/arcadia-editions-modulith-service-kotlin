package com.arcadiaeditions.catalog.products.domain
object ProductTransitions {

    fun ensureCanUpdateProduct(entity: Product) {
        require("updateProduct", entity, ProductStatus.DRAFT, ProductStatus.PUBLISHED)
    }

    fun ensureCanPublishProduct(entity: Product) {
        require("publishProduct", entity, ProductStatus.DRAFT)
    }

    fun ensureCanRetireProduct(entity: Product) {
        require("retireProduct", entity, ProductStatus.DRAFT, ProductStatus.PUBLISHED)
    }

    private fun require(command: String, entity: Product, vararg allowed: ProductStatus) {
        val current = entity.status
        if (current != null && allowed.any { it == current }) {
            return
        }
        throw InvalidStateTransitionException(
            entityType = entity::class.simpleName ?: "Product",
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
