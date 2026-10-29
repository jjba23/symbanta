/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.domain

/**
 * Errors the application layer raises on purpose. Inbound adapters translate these into
 * transport specific responses (HTTP status codes, for instance).
 */
sealed class SymbantaException(
    message: String,
) : RuntimeException(message)

class PropertyNotFoundException(
    domain: String,
) : SymbantaException("No property is registered for domain '$domain'")

class EventValidationException(
    message: String,
) : SymbantaException(message)

class InvalidPeriodException(
    message: String,
) : SymbantaException(message)
