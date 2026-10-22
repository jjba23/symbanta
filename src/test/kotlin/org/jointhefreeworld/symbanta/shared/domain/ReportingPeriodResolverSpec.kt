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

package org.jointhefreeworld.symbanta.shared.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.fixedClock
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.shared.domain.InvalidPeriodException
import org.jointhefreeworld.symbanta.shared.domain.TimePeriod
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class ReportingPeriodResolverSpec :
    FunSpec({

        fun resolve(
            period: TimePeriod,
            startDate: LocalDate? = null,
            endDate: LocalDate? = null,
        ): DateRange = ReportingPeriodResolver.resolve(period, startDate, endDate, fixedClock)

        context("resolves calendar periods in the configured timezone") {
            withData(
                TimePeriod.TODAY to
                    DateRange(Instant.parse("2026-01-14T23:00:00Z"), Instant.parse("2026-01-15T00:00:00Z")),
                TimePeriod.YESTERDAY to
                    DateRange(Instant.parse("2026-01-13T23:00:00Z"), Instant.parse("2026-01-14T23:00:00Z")),
                TimePeriod.LAST_7_DAYS to
                    DateRange(Instant.parse("2026-01-08T00:00:00Z"), Instant.parse("2026-01-15T00:00:00Z")),
                TimePeriod.THIS_MONTH to
                    DateRange(Instant.parse("2025-12-31T23:00:00Z"), Instant.parse("2026-01-15T00:00:00Z")),
                TimePeriod.ALL_TIME to
                    DateRange(Instant.EPOCH, Instant.parse("2026-01-15T00:00:00Z")),
            ) { (period, expected) ->
                resolve(period) shouldBe expected
            }
        }

        test("CUSTOM spans whole days, inclusive of the end date") {
            resolve(
                TimePeriod.CUSTOM,
                startDate = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 1, 10),
            ) shouldBe
                DateRange(
                    Instant.parse("2025-12-31T23:00:00Z"),
                    Instant.parse("2026-01-10T23:00:00Z"),
                )
        }

        test("CUSTOM requires both dates") {
            shouldThrow<InvalidPeriodException> {
                resolve(TimePeriod.CUSTOM, startDate = LocalDate.of(2026, 1, 1))
            }
        }

        test("CUSTOM rejects an inverted range") {
            shouldThrow<InvalidPeriodException> {
                resolve(
                    TimePeriod.CUSTOM,
                    startDate = LocalDate.of(2026, 1, 10),
                    endDate = LocalDate.of(2026, 1, 1),
                )
            }
        }

        context("chooses hourly buckets for short windows only") {
            withData(
                TimePeriod.TODAY to TimeGranularity.HOUR,
                TimePeriod.YESTERDAY to TimeGranularity.HOUR,
                TimePeriod.LAST_24_HOURS to TimeGranularity.HOUR,
                TimePeriod.LAST_7_DAYS to TimeGranularity.DAY,
                TimePeriod.LAST_30_DAYS to TimeGranularity.DAY,
            ) { (period, expected) ->
                val range =
                    DateRange(
                        Instant.parse("2026-01-08T00:00:00Z"),
                        Instant.parse("2026-01-15T00:00:00Z"),
                    )
                ReportingPeriodResolver.granularity(period, range) shouldBe expected
            }

            test("CUSTOM uses the window length") {
                val short = DateRange(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"))
                val long = DateRange(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-20T00:00:00Z"))

                ReportingPeriodResolver.granularity(TimePeriod.CUSTOM, short) shouldBe TimeGranularity.HOUR
                ReportingPeriodResolver.granularity(TimePeriod.CUSTOM, long) shouldBe TimeGranularity.DAY
                Duration.between(long.from, long.to).toDays() shouldBe 19
            }
        }
    })
