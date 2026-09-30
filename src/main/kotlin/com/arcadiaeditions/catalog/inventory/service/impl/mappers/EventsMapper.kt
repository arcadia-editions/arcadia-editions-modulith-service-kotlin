package com.arcadiaeditions.catalog.inventory.service.impl.mappers

import com.arcadiaeditions.catalog.inventory.domain.*
import com.arcadiaeditions.catalog.inventory.service.dtos.*
import com.arcadiaeditions.common.mappers.BaseMapper
import java.time.Instant
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.factory.Mappers

@Mapper(uses = [BaseMapper::class])
interface EventsMapper {

    companion object {
        val INSTANCE: EventsMapper = Mappers.getMapper(EventsMapper::class.java)
    }

    // Auto-mapped version (pre-existing); not used anymore but kept for compatibility
    // The unmapped warnings are suppressed since the new overloads handle proper mapping.
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "reference", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    fun asStockReceived(
        inventoryPosition: InventoryPosition
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReceived

    // Auto-mapped version (pre-existing); not used anymore but kept for compatibility
    // The unmapped warnings are suppressed since the new overloads handle proper mapping.
    @Mapping(target = "quantityDelta", ignore = true)
    @Mapping(target = "reason", ignore = true)
    @Mapping(target = "reference", ignore = true)
    @Mapping(target = "adjustedAt", ignore = true)
    fun asStockAdjusted(
        inventoryPosition: InventoryPosition
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockAdjusted

    // G-CI-05: reservationId/sku/quantity match by name -> auto-mapped.
    fun asStockReservationConfirmedItem(
        stockReservation: StockReservation
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReservationConfirmedItem

    fun asStockReleasedItem(
        stockReservation: StockReservation
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReleasedItem

    // G-CI-03/G-CI-05: StockReservationConfirmed is one event per order (not per reservation), and
    // its reservationId/sku/quantity live on the confirmed reservations, not on the
    // ConfirmStockReservationInput -> composed manually from the order id/timestamp plus the saved
    // reservations.
    fun asStockReservationConfirmed(
        orderId: String?,
        confirmedAt: Instant?,
        reservations: List<StockReservation>,
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReservationConfirmed {
        val event =
            com.arcadiaeditions.catalog.inventory.domain.events.StockReservationConfirmed(
                orderId = orderId,
                confirmedAt = confirmedAt,
            )
        reservations.forEach { event.addItems(asStockReservationConfirmedItem(it)) }
        return event
    }

    // G-CI-02/G-CI-05: same shape as above; `reason` is the release reason for the whole order, not
    // per reservation (each reservation only knows its own `releaseReason` once persisted).
    fun asStockReleased(
        orderId: String?,
        reason: StockReleaseReason?,
        releasedAt: Instant?,
        reservations: List<StockReservation>,
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReleased {
        val event =
            com.arcadiaeditions.catalog.inventory.domain.events.StockReleased(
                orderId = orderId,
                reason = reason,
                releasedAt = releasedAt,
            )
        reservations.forEach { event.addItems(asStockReleasedItem(it)) }
        return event
    }

    // G-CI-07: StockReceived with all required fields from ReceiveStockInput + timestamp.
    // Maps from individual parameters rather than the entity alone, since the event needs
    // input fields like `reason` and `reference` that don't live on InventoryPosition.
    fun asStockReceived(
        sku: String?,
        quantity: Integer?,
        availableQuantity: Integer?,
        reservedQuantity: Integer?,
        soldQuantity: Integer?,
        reason: String?,
        reference: String?,
        receivedAt: Instant?,
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockReceived {
        return com.arcadiaeditions.catalog.inventory.domain.events.StockReceived(
            sku = sku,
            quantity = quantity,
            availableQuantity = availableQuantity,
            reservedQuantity = reservedQuantity,
            soldQuantity = soldQuantity,
            reason = reason,
            reference = reference,
            receivedAt = receivedAt,
        )
    }

    // G-CI-07: StockAdjusted with all required fields from AdjustStockInput + timestamp.
    // Maps from individual parameters rather than the entity alone, since the event needs
    // input fields like `reason` and `reference` that don't live on InventoryPosition.
    fun asStockAdjusted(
        sku: String?,
        quantityDelta: Integer?,
        availableQuantity: Integer?,
        reservedQuantity: Integer?,
        soldQuantity: Integer?,
        reason: StockAdjustmentReason?,
        reference: String?,
        adjustedAt: Instant?,
    ): com.arcadiaeditions.catalog.inventory.domain.events.StockAdjusted {
        return com.arcadiaeditions.catalog.inventory.domain.events.StockAdjusted(
            sku = sku,
            quantityDelta = quantityDelta,
            availableQuantity = availableQuantity,
            reservedQuantity = reservedQuantity,
            soldQuantity = soldQuantity,
            reason = reason,
            reference = reference,
            adjustedAt = adjustedAt,
        )
    }
}
