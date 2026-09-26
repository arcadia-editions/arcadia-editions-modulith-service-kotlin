package com.arcadiaeditions.catalog.inventory.events.processing

import com.arcadiaeditions.catalog.inventory.domain.events.*
import com.arcadiaeditions.catalog.inventory.events.processing.mappers.PaymentsProcessingEventsListenerMapper
import com.arcadiaeditions.catalog.inventory.service.*
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Internal domain event listeners for PaymentsProcessingZdl.
 *
 * This class is generated once and can be customized safely.
 */
@Component
@org.jmolecules.architecture.layered.InterfaceLayer
class PaymentsProcessingEventsListener(
    private val inventoryService: InventoryService,
    private val mapper: PaymentsProcessingEventsListenerMapper,
) {

    @ApplicationModuleListener
    fun onPaymentDeclined(event: com.arcadiaeditions.payments.processing.domain.events.PaymentDeclined) {
        inventoryService.releaseStock(mapper.asReleaseStockInput(event))
    }

    @ApplicationModuleListener
    fun onPaymentRetryExhausted(event: com.arcadiaeditions.payments.processing.domain.events.PaymentRetryExhausted) {
        inventoryService.releaseStock(mapper.asReleaseStockInput(event))
    }

    @ApplicationModuleListener
    fun onPaymentVoided(event: com.arcadiaeditions.payments.processing.domain.events.PaymentVoided) {
        inventoryService.releaseStock(mapper.asReleaseStockInput(event))
    }
}
