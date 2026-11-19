package com.arcadiaeditions.orders

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Every cross-module type orders uses must be listed here; anything else is a Modulith violation.
 * Event listeners count too: a listener parameter is a type dependency on the producer's event class.
 */
@PackageInfo
@ApplicationModule(
    displayName = "Arcadia Editions - Orders Checkout",
    allowedDependencies = [
        // direct, synchronous call: reserve / confirm stock
        "catalog.inventory :: InventoryService",
        // events consumed
        "catalog.inventory :: events.StockReleased",
        "payments :: events.PaymentAuthorized",
        "common :: mappers",
    ],
)
class ModuleMetadata
