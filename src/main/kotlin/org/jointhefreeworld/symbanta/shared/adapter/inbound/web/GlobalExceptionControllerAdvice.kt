/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.adapter.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.shared.domain.EventValidationException
import org.jointhefreeworld.symbanta.shared.domain.InvalidPeriodException
import org.jointhefreeworld.symbanta.shared.domain.PropertyNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Translates application and domain failures into RFC 7807 problem details, so use cases and
 * controllers never need to know about HTTP status codes.
 */
@RestControllerAdvice
class GlobalExceptionControllerAdvice {
    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(PropertyNotFoundException::class)
    fun handlePropertyNotFound(ex: PropertyNotFoundException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.NOT_FOUND, ex.message ?: "Property not found")

    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        IllegalArgumentException::class,
        EventValidationException::class,
        InvalidPeriodException::class,
    )
    fun handleBadRequestExceptions(ex: Exception): ResponseEntity<ProblemDetail> {
        val detail =
            when (ex) {
                is MissingServletRequestParameterException -> "Required request parameter '${ex.parameterName}' is missing"
                is MethodArgumentTypeMismatchException -> "Invalid value for parameter '${ex.name}': '${ex.value}'"
                else -> ex.message ?: "Invalid request parameters"
            }

        return problem(HttpStatus.BAD_REQUEST, detail)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<ProblemDetail> {
        logger.error(ex) { "Unhandled error while serving request" }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "An unexpected error occurred")
    }

    private fun problem(
        status: HttpStatus,
        detail: String,
    ): ResponseEntity<ProblemDetail> = ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail))
}
