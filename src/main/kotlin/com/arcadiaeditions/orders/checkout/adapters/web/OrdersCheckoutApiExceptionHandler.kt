package com.arcadiaeditions.orders.checkout.adapters.web

import com.arcadiaeditions.orders.checkout.core.inbound.StockUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 409 body of startOrderCheckout when stock is gone (P-03 A). */
data class StockUnavailableBody(
    var orderId: String? = null,
    var unavailableItems: List<StockUnavailableItemBody>? = emptyList(),
)

data class StockUnavailableItemBody(
    var sku: String? = null,
    var requestedQuantity: Int? = null,
    var availableQuantity: Int? = null,
)

/** Maps checkout core exceptions to HTTP answers (hand-written, next to the generated controller). */
@RestControllerAdvice(assignableTypes = [OrdersCheckoutApiController::class])
class OrdersCheckoutApiExceptionHandler {

    @ExceptionHandler(StockUnavailableException::class)
    fun handleStockUnavailable(ex: StockUnavailableException): ResponseEntity<StockUnavailableBody> {
        val event = ex.event
        val body =
            StockUnavailableBody(
                orderId = event.orderId,
                unavailableItems =
                    event.unavailableItems?.map {
                        StockUnavailableItemBody(it.sku, it.requestedQuantity?.toInt(), it.availableQuantity?.toInt())
                    } ?: emptyList(),
            )
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(body)
    }
}
