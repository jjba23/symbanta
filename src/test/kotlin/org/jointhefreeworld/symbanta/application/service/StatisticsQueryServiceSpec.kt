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

package org.jointhefreeworld.symbanta.application.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.TestPrelude.fixedClock
import org.jointhefreeworld.symbanta.application.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.application.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.domain.model.ConsoleLogEntry
import org.jointhefreeworld.symbanta.domain.model.ConsoleLogLevel
import org.jointhefreeworld.symbanta.domain.model.CountryStat
import org.jointhefreeworld.symbanta.domain.model.DateRange
import org.jointhefreeworld.symbanta.domain.model.EventStat
import org.jointhefreeworld.symbanta.domain.model.PeriodSelection
import org.jointhefreeworld.symbanta.domain.model.RouteStat
import org.jointhefreeworld.symbanta.domain.model.ScreenSizeStat
import org.jointhefreeworld.symbanta.domain.model.StatsSummary
import org.jointhefreeworld.symbanta.domain.model.TimeGranularity
import org.jointhefreeworld.symbanta.domain.model.TimePeriod
import org.jointhefreeworld.symbanta.domain.model.TimeSeriesPoint
import org.jointhefreeworld.symbanta.domain.model.UserAgentStat
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class StatisticsQueryServiceSpec :
    FunSpec({

        val queries = mock<AnalyticsQueryPort>()
        val clock =
            object : ClockPort {
                override fun now() = fixedClock.instant()
            }
        val service = StatisticsQueryService(queries, clock)

        val customQuery =
            StatsQuery(
                domain = "example.com",
                period =
                    PeriodSelection(
                        period = TimePeriod.CUSTOM,
                        startDate = LocalDate.of(2026, 1, 1),
                        endDate = LocalDate.of(2026, 1, 10),
                    ),
            )
        val customRange =
            DateRange(
                from = amsterdamStartOfDayInstant(2026, 1, 1),
                to = amsterdamStartOfDayInstant(2026, 1, 11),
            )

        context("summary") {
            test("resolves the requested period and returns the resolved window") {
                whenever(queries.summary(eq("example.com"), any())).thenReturn(
                    StatsSummary(
                        pageViews = 10,
                        visitors = 4,
                        bounceRate = BigDecimal("25.00"),
                        averageDurationMillis = BigDecimal("1500.0"),
                    ),
                )

                val result = service.summary(customQuery)

                result.from shouldBe customRange.from
                result.to shouldBe customRange.to
                result.summary.pageViews shouldBe 10
                verify(queries).summary("example.com", customRange)
            }
        }

        context("time series") {
            test("uses hourly buckets for last 24 hours") {
                whenever(queries.timeSeries(any(), any(), any())).thenReturn(
                    listOf(TimeSeriesPoint(fixedClock.instant(), pageViews = 1, visitors = 1)),
                )

                val result =
                    service.timeSeries(
                        StatsQuery("example.com", PeriodSelection(TimePeriod.LAST_24_HOURS)),
                    )

                result.granularity shouldBe TimeGranularity.HOUR
                verify(queries).timeSeries(eq("example.com"), any(), eq(TimeGranularity.HOUR))
            }

            test("uses daily buckets for a week") {
                whenever(queries.timeSeries(any(), any(), any())).thenReturn(emptyList())

                val result =
                    service.timeSeries(
                        StatsQuery("example.com", PeriodSelection(TimePeriod.LAST_7_DAYS)),
                    )

                result.granularity shouldBe TimeGranularity.DAY
            }
        }

        context("breakdowns") {
            test("aggregates raw screen sizes into readable buckets") {
                whenever(queries.screenSizes(any(), any())).thenReturn(
                    listOf(
                        ScreenSizeStat(1920, 1080, pageViews = 2, visitors = 2),
                        ScreenSizeStat(1280, 720, pageViews = 1, visitors = 1),
                    ),
                )

                val result = service.screenSizes(customQuery)

                result.items.map { it.size } shouldContainExactly
                    listOf("900p - 1080p", "600p - 720p")
                result.items.first().pageViews shouldBe 2
            }

            test("aggregates raw user agents into platform/browser labels") {
                whenever(queries.userAgents(any(), any())).thenReturn(
                    listOf(
                        UserAgentStat(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                            pageViews = 3,
                            visitors = 2,
                        ),
                        UserAgentStat(
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1",
                            pageViews = 5,
                            visitors = 5,
                        ),
                    ),
                )

                val result = service.userAgents(customQuery)

                result.items.map { it.label } shouldContainExactly
                    listOf("iOS / iPhone, Safari", "macOS, Chrome")
                result.items.first().pageViews shouldBe 5
            }

            test("passes event type, country and route breakdowns through untouched") {
                whenever(queries.events(any(), any())).thenReturn(
                    listOf(EventStat("signup-click", occurrences = 7, visitors = 3)),
                )
                whenever(queries.countries(any(), any())).thenReturn(
                    listOf(CountryStat("NL", pageViews = 9, visitors = 4)),
                )
                whenever(queries.routes(any(), any())).thenReturn(
                    listOf(RouteStat("/home", pageViews = 9, visitors = 4)),
                )

                service
                    .events(customQuery)
                    .items
                    .single()
                    .eventName shouldBe "signup-click"
                service
                    .countries(customQuery)
                    .items
                    .single()
                    .country shouldBe "NL"
                service
                    .routes(customQuery)
                    .items
                    .single()
                    .route shouldBe "/home"

                verify(queries).events("example.com", customRange)
                verify(queries).countries("example.com", customRange)
                verify(queries).routes("example.com", customRange)
            }
        }

        context("console logs") {
            test("clamps the requested limit") {
                whenever(queries.consoleLogs(any(), any(), any())).thenReturn(emptyList())

                ConsoleLogService(queries).recent(
                    org.jointhefreeworld.symbanta.application.port.inbound.ConsoleLogQuery(
                        domain = "example.com",
                        level = ConsoleLogLevel.ERROR,
                        limit = 10_000,
                    ),
                )

                verify(queries).consoleLogs("example.com", ConsoleLogLevel.ERROR, ConsoleLogService.MAX_LIMIT)
            }

            test("maps entries one to one") {
                val entry =
                    ConsoleLogEntry(
                        timestamp = fixedClock.instant(),
                        level = ConsoleLogLevel.WARN,
                        message = "deprecated API",
                        url = "https://example.com",
                        sessionId = "s1",
                        country = "NL",
                    )
                whenever(queries.consoleLogs(any(), any(), any())).thenReturn(listOf(entry))

                ConsoleLogService(queries)
                    .recent(
                        org.jointhefreeworld.symbanta.application.port.inbound.ConsoleLogQuery(
                            "example.com",
                            ConsoleLogLevel.WARN,
                            10,
                        ),
                    ).single() shouldBe entry
            }
        }
    })
