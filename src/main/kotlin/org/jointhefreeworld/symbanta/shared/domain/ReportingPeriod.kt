/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Reporting windows understood by the API. Kept in the domain so period semantics do not
 * depend on the generated OpenAPI DTOs.
 */
enum class TimePeriod {
    TODAY,
    YESTERDAY,
    LAST_24_HOURS,
    LAST_3_DAYS,
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    LAST_MONTH,
    LAST_6_MONTHS,
    LAST_12_MONTHS,
    THIS_YEAR,
    LAST_YEAR,
    ALL_TIME,
    CUSTOM,
}

/** Inclusive/exclusive half-open query window: `from <= created_at < to` for aggregates. */
data class DateRange(
    val from: Instant,
    val to: Instant,
) {
    init {
        require(!to.isBefore(from)) { "End of range must not be before its start" }
    }
}

/** A period request exactly as it arrives from a user, before it is resolved against a clock. */
data class PeriodSelection(
    val period: TimePeriod,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)
