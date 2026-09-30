package com.arcadiaeditions.config

import com.arcadiaeditions.catalog.inventory.domain.StockReservationTransitions
import com.arcadiaeditions.catalog.products.domain.ProductTransitions
import com.arcadiaeditions.common.DomainRejectionException
import com.arcadiaeditions.fulfillment.shipping.domain.FulfillmentTransitions
import com.arcadiaeditions.orders.checkout.domain.OrderTransitions
import com.arcadiaeditions.payments.processing.domain.PaymentTransitions
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice
class WebExceptionsTranslator : ResponseEntityExceptionHandler() {

    /** X-01: business key lookups (`orderRepository.findByOrderId(...) ?: throw NoSuchElementException(...)`). */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, request: WebRequest): ResponseEntity<Any>? {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Entity not found")
        return createResponseEntity(body, HttpHeaders(), HttpStatus.NOT_FOUND, request)
    }

    /**
     * X-01: `*Transitions.ensureCan…` guards. Each bounded context declares its own nested
     * `InvalidStateTransitionException` (they don't share a common base - see `*Transitions.kt`), so
     * every one of them is listed here explicitly.
     */
    @ExceptionHandler(
        OrderTransitions.InvalidStateTransitionException::class,
        StockReservationTransitions.InvalidStateTransitionException::class,
        PaymentTransitions.InvalidStateTransitionException::class,
        FulfillmentTransitions.InvalidStateTransitionException::class,
        ProductTransitions.InvalidStateTransitionException::class,
    )
    fun handleInvalidStateTransition(ex: Exception, request: WebRequest): ResponseEntity<Any>? {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Invalid state transition")
        return createResponseEntity(body, HttpHeaders(), HttpStatus.CONFLICT, request)
    }

    /** X-01: business rejections, e.g. [StockUnavailableException][com.arcadiaeditions.orders.checkout.service.StockUnavailableException]. */
    @ExceptionHandler(DomainRejectionException::class)
    fun handleDomainRejection(ex: DomainRejectionException, request: WebRequest): ResponseEntity<Any>? {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.message ?: "Request rejected")
        return createResponseEntity(body, HttpHeaders(), HttpStatus.CONFLICT, request)
    }

    /** X-01: validation failures raised directly by service code (as opposed to `@Valid` bean validation). */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<Any>? {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")
        return createResponseEntity(body, HttpHeaders(), HttpStatus.BAD_REQUEST, request)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val body = ex.updateAndGetBody(messageSource, LocaleContextHolder.getLocale())
        fillBindingErrors(body, ex)
        return createResponseEntity(body, headers, status, request)
    }

    private fun fillBindingErrors(body: ProblemDetail, errors: MethodArgumentNotValidException) {
        errors.bindingResult.globalErrors.forEach { error ->
            body.setProperty(error.objectName, error.toString())
        }
        errors.bindingResult.fieldErrors.forEach { error ->
            body.setProperty(error.objectName + "." + error.field, error.defaultMessage)
        }
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (statusCode.is5xxServerError) {
            logger.error(String.format("Internal Error: %s %s", request.contextPath, statusCode), ex)
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }
}
