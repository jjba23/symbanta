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

package org.jointhefreeworld.symbanta.domain.model

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
