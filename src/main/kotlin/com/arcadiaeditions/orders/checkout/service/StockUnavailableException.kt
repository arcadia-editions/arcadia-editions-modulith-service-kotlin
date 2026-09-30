package com.arcadiaeditions.orders.checkout.service

import com.arcadiaeditions.common.DomainRejectionException

/**
 * Thrown by `startOrderCheckout` when `InventoryService.reserveStock` reports
 * `ReserveStockOutcome.UNAVAILABLE` (Q-03/Q-15: no order is persisted in that branch). The
 * `StockUnavailable` event is still published; see `@Transactional(noRollbackFor = ...)` on
 * `OrdersCheckoutServiceImpl.startOrderCheckout`. [WebExceptionsTranslator][
 * com.arcadiaeditions.config.WebExceptionsTranslator] maps it to HTTP 409.
 */
class StockUnavailableException(message: String) : DomainRejectionException(message)
