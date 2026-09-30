package com.arcadiaeditions.common

/**
 * Base type for business-rule rejections that are not an invalid state transition and not "not
 * found" (e.g. stock unavailable, a negative stock adjustment). [WebExceptionsTranslator][
 * com.arcadiaeditions.config.WebExceptionsTranslator] maps every subtype to HTTP 409.
 */
open class DomainRejectionException(message: String) : RuntimeException(message)
