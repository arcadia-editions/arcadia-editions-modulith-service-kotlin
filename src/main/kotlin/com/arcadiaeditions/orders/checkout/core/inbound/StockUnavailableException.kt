package com.arcadiaeditions.orders.checkout.core.inbound

import com.arcadiaeditions.orders.checkout.core.domain.events.StockUnavailable

/** startOrderCheckout found no stock: no Order was created (A-05). Carries the emitted [StockUnavailable]. */
class StockUnavailableException(val event: StockUnavailable) : RuntimeException("Stock unavailable")
