/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.domain

import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.shared.domain.InvalidPeriodException
import org.jointhefreeworld.symbanta.shared.domain.TimePeriod
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure translation of a human reporting period into a concrete instant window.
 *
 * Kept framework-free so it can be unit tested with a fixed clock and reused by any
 * inbound adapter.
 */
object ReportingPeriodResolver {
    const val TIMEZONE = "Europe/Amsterdam"
    val ZONE_ID: ZoneId = ZoneId.of(TIMEZONE)

    fun resolve(
        period: TimePeriod,
        startDate: LocalDate?,
        endDate: LocalDate?,
        now: Instant,
    ): DateRange {
        val zdt = now.atZone(ZONE_ID)
        val today = zdt.toLocalDate()

        return when (period) {
            TimePeriod.TODAY -> DateRange(today.atStartOfDay(ZONE_ID).toInstant(), now)

            TimePeriod.YESTERDAY ->
                DateRange(
                    today.minusDays(1).atStartOfDay(ZONE_ID).toInstant(),
                    today.atStartOfDay(ZONE_ID).toInstant(),
                )

            TimePeriod.LAST_24_HOURS -> DateRange(zdt.minusHours(24).toInstant(), now)

            TimePeriod.LAST_3_DAYS -> DateRange(zdt.minusDays(3).toInstant(), now)

            TimePeriod.LAST_7_DAYS -> DateRange(zdt.minusDays(7).toInstant(), now)

            TimePeriod.LAST_30_DAYS -> DateRange(zdt.minusDays(30).toInstant(), now)

            TimePeriod.THIS_MONTH -> DateRange(today.withDayOfMonth(1).atStartOfDay(ZONE_ID).toInstant(), now)

            TimePeriod.LAST_MONTH -> {
                val firstDayOfThisMonth = today.withDayOfMonth(1)
                DateRange(
                    firstDayOfThisMonth.minusMonths(1).atStartOfDay(ZONE_ID).toInstant(),
                    firstDayOfThisMonth.atStartOfDay(ZONE_ID).toInstant(),
                )
            }

            TimePeriod.LAST_6_MONTHS -> DateRange(zdt.minusMonths(6).toInstant(), now)

            TimePeriod.LAST_12_MONTHS -> DateRange(zdt.minusMonths(12).toInstant(), now)

            TimePeriod.THIS_YEAR -> DateRange(today.withDayOfYear(1).atStartOfDay(ZONE_ID).toInstant(), now)

            TimePeriod.LAST_YEAR -> {
                val firstDayOfThisYear = today.withDayOfYear(1)
                DateRange(
                    firstDayOfThisYear.minusYears(1).atStartOfDay(ZONE_ID).toInstant(),
                    firstDayOfThisYear.atStartOfDay(ZONE_ID).toInstant(),
                )
            }

            TimePeriod.ALL_TIME -> DateRange(Instant.EPOCH, now)

            TimePeriod.CUSTOM -> {
                if (startDate == null || endDate == null) {
                    throw InvalidPeriodException("Both start-date and end-date are required when period is CUSTOM")
                }
                if (endDate.isBefore(startDate)) {
                    throw InvalidPeriodException("end-date must not be before start-date")
                }
                DateRange(
                    startDate.atStartOfDay(ZONE_ID).toInstant(),
                    endDate.plusDays(1).atStartOfDay(ZONE_ID).toInstant(),
                )
            }
        }
    }

    fun resolve(
        period: TimePeriod,
        startDate: LocalDate?,
        endDate: LocalDate?,
        clock: Clock,
    ): DateRange = resolve(period, startDate, endDate, clock.instant())

    /** Hourly buckets for short windows, daily buckets otherwise. */
    fun granularity(
        period: TimePeriod,
        range: DateRange,
    ): TimeGranularity =
        when (period) {
            TimePeriod.TODAY,
            TimePeriod.YESTERDAY,
            TimePeriod.LAST_24_HOURS,
            -> TimeGranularity.HOUR

            TimePeriod.CUSTOM ->
                if (Duration.between(range.from, range.to).toHours() <= 48) {
                    TimeGranularity.HOUR
                } else {
                    TimeGranularity.DAY
                }

            else -> TimeGranularity.DAY
        }
}
