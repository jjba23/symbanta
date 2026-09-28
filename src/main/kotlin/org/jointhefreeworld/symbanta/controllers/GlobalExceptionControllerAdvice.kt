/* symbanta
*
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*
* symbanta is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* symbanta is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with symbanta.  If not, see <https://www.gnu.org/licenses/>.
*/

package org.jointhefreeworld.symbanta.controllers

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionControllerAdvice {
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        IllegalArgumentException::class,
    )
    fun handleBadRequestExceptions(ex: Exception): ResponseEntity<ProblemDetail> {
        val detail =
            when (ex) {
                is MissingServletRequestParameterException -> "Required request parameter '${ex.parameterName}' is missing"
                is MethodArgumentTypeMismatchException -> "Invalid value for parameter '${ex.name}': '${ex.value}'"
                else -> ex.message ?: "Invalid request parameters"
            }

        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception): ResponseEntity<ProblemDetail> {
        val problemDetail =
            ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.message ?: "An unexpected error occurred",
            )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail)
    }
}
