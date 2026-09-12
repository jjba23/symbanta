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

import org.jointhefreeworld.symbanta.domain.TimeGranularity
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import java.time.*

const val TIMEZONE = "Europe/Amsterdam"
val ZONE_ID: ZoneId = ZoneId.of(TIMEZONE)

data class DateRange(
    val from: Instant,
    val to: Instant,
)

object PeriodResolver {
    fun resolve(
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
        clock: Clock,
    ): DateRange {
        val now = clock.instant()
        val zdt = now.atZone(ZONE_ID)
        val today = zdt.toLocalDate()

        return when (period) {
            PeriodDto.TODAY -> {
                DateRange(
                    from = today.atStartOfDay(ZONE_ID).toInstant(),
                    to = now,
                )
            }

            PeriodDto.YESTERDAY -> {
                DateRange(
                    from = today.minusDays(1).atStartOfDay(ZONE_ID).toInstant(),
                    to = today.atStartOfDay(ZONE_ID).toInstant(),
                )
            }

            PeriodDto.LAST_24_HOURS -> {
                DateRange(
                    from = zdt.minusHours(24).toInstant(),
                    to = now,
                )
            }

            PeriodDto.LAST_3_DAYS -> {
                DateRange(
                    from = zdt.minusDays(3).toInstant(),
                    to = now,
                )
            }

            PeriodDto.LAST_7_DAYS -> {
                DateRange(
                    from = zdt.minusDays(7).toInstant(),
                    to = now,
                )
            }

            PeriodDto.LAST_30_DAYS -> {
                DateRange(
                    from = zdt.minusDays(30).toInstant(),
                    to = now,
                )
            }

            PeriodDto.THIS_MONTH -> {
                DateRange(
                    from = today.withDayOfMonth(1).atStartOfDay(ZONE_ID).toInstant(),
                    to = now,
                )
            }

            PeriodDto.LAST_MONTH -> {
                val firstDayOfThisMonth = today.withDayOfMonth(1)
                DateRange(
                    from = firstDayOfThisMonth.minusMonths(1).atStartOfDay(ZONE_ID).toInstant(),
                    to = firstDayOfThisMonth.atStartOfDay(ZONE_ID).toInstant(),
                )
            }

            PeriodDto.LAST_6_MONTHS -> {
                DateRange(
                    from = zdt.minusMonths(6).toInstant(),
                    to = now,
                )
            }

            PeriodDto.LAST_12_MONTHS -> {
                DateRange(
                    from = zdt.minusMonths(12).toInstant(),
                    to = now,
                )
            }

            PeriodDto.THIS_YEAR -> {
                DateRange(
                    from = today.withDayOfYear(1).atStartOfDay(ZONE_ID).toInstant(),
                    to = now,
                )
            }

            PeriodDto.LAST_YEAR -> {
                val firstDayOfThisYear = today.withDayOfYear(1)
                DateRange(
                    from = firstDayOfThisYear.minusYears(1).atStartOfDay(ZONE_ID).toInstant(),
                    to = firstDayOfThisYear.atStartOfDay(ZONE_ID).toInstant(),
                )
            }

            PeriodDto.ALL_TIME -> {
                DateRange(
                    from = Instant.EPOCH,
                    to = now,
                )
            }

            PeriodDto.CUSTOM -> {
                checkNotNull(startDate) { "startDate must not be null when period is CUSTOM" }
                checkNotNull(endDate) { "endDate must not be null when period is CUSTOM" }
                DateRange(
                    from = startDate.atStartOfDay(ZONE_ID).toInstant(),
                    to = endDate.plusDays(1).atStartOfDay(ZONE_ID).toInstant(),
                )
            }
        }
    }

    fun resolveGranularity(
        period: PeriodDto,
        from: Instant,
        to: Instant,
    ): TimeGranularity =
        when (period) {
            PeriodDto.TODAY,
            PeriodDto.YESTERDAY,
            PeriodDto.LAST_24_HOURS,
            -> {
                TimeGranularity.HOUR
            }

            PeriodDto.CUSTOM -> {
                if (Duration.between(from, to).toHours() <= 48) {
                    TimeGranularity.HOUR
                } else {
                    TimeGranularity.DAY
                }
            }

            else -> {
                TimeGranularity.DAY
            }
        }
}
