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

package org.jointhefreeworld.symbanta.feature.statistics.port.outbound

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.BASE_TIME
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.event
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.range
import org.jointhefreeworld.symbanta.support.AnalyticsQueryFixture
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Behaviour every [AnalyticsQueryPort] adapter must satisfy.
 *
 * The suite seeds data through the [AnalyticsQueryFixture] and asserts only domain-typed
 * results, so it applies unchanged to a relational store, a columnar store or an in-memory
 * implementation. A new adapter passes itself in and is then held to the same aggregation,
 * ordering and window semantics as the PostgreSQL one.
 */
abstract class AnalyticsQueryPortContract(
    fixture: AnalyticsQueryFixture,
) : FunSpec({

        lateinit var queries: AnalyticsQueryPort
        lateinit var property: Property

        beforeSpec {
            queries = fixture.createQueries()
        }

        beforeTest {
            fixture.reset()
            queries = fixture.createQueries()
            property = fixture.createProperty("analytics.example")
        }

        context("summary") {
            test("counts page views and distinct sessions") {
                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1"),
                        event(property, sessionId = "s-1", createdAt = BASE_TIME.plusSeconds(5)),
                        event(property, sessionId = "s-2"),
                    ),
                )

                val summary = queries.summary(property.domain, range())

                summary.pageViews shouldBe 3
                summary.visitors shouldBe 2
            }

            test("computes bounce rate and average active duration per session") {
                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1"),
                        event(property, sessionId = "s-1", createdAt = BASE_TIME.plusSeconds(5)),
                        event(property, sessionId = "s-2"),
                    ),
                )

                val summary = queries.summary(property.domain, range())

                summary.bounceRate.compareTo(BigDecimal("50.00")) shouldBe 0
                summary.averageDurationMillis.toDouble() shouldBe 5_000.0
            }

            test("returns zeroes for an unknown domain") {
                val summary = queries.summary("missing.example", range())

                summary.pageViews shouldBe 0
                summary.visitors shouldBe 0
            }

            test("ignores events outside the requested window") {
                fixture.write(listOf(event(property)))

                val after = range(from = BASE_TIME.plusSeconds(60), to = BASE_TIME.plusSeconds(120))

                queries.summary(property.domain, after).pageViews shouldBe 0
            }
        }

        context("timeSeries") {
            test("returns hourly buckets, zero-filled, with page views and visitors") {
                val from = BASE_TIME.truncatedTo(ChronoUnit.HOURS)
                val to = from.plusSeconds(7_200)

                fixture.write(listOf(event(property, createdAt = from.plusSeconds(300))))

                val points = queries.timeSeries(property.domain, range(from = from, to = to), TimeGranularity.HOUR)

                points shouldHaveSize 2
                points.first().pageViews shouldBe 1
                points.first().visitors shouldBe 1
                points.sumOf { it.pageViews } shouldBe 1
                points.last().pageViews shouldBe 0
            }

            test("returns daily buckets at local midnight of the reporting time zone") {
                val from = amsterdamStartOfDayInstant(2026, 1, 9)
                val to = amsterdamStartOfDayInstant(2026, 1, 11)

                fixture.write(listOf(event(property, createdAt = BASE_TIME)))

                val points = queries.timeSeries(property.domain, range(from = from, to = to), TimeGranularity.DAY)

                points.map { it.timestamp } shouldContainExactly
                    listOf(Instant.parse("2026-01-08T23:00:00Z"), Instant.parse("2026-01-09T23:00:00Z"))
                points.map { it.pageViews } shouldContainExactly listOf(0L, 1L)
                points.last().visitors shouldBe 1
            }

            test("assigns an event just after local midnight to that local day") {
                // 2026-01-09T00:30 in Amsterdam is still 2026-01-08 in UTC.
                fixture.write(listOf(event(property, createdAt = Instant.parse("2026-01-08T23:30:00Z"))))

                val points =
                    queries.timeSeries(
                        property.domain,
                        range(from = amsterdamStartOfDayInstant(2026, 1, 9), to = amsterdamStartOfDayInstant(2026, 1, 10)),
                        TimeGranularity.DAY,
                    )

                points shouldHaveSize 1
                points.single().timestamp shouldBe amsterdamStartOfDayInstant(2026, 1, 9)
                points.single().pageViews shouldBe 1
            }

            test("counts only page views as page views but every session as a visitor") {
                val from = BASE_TIME.truncatedTo(ChronoUnit.HOURS)

                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1", createdAt = from.plusSeconds(60)),
                        event(property, name = "signup-click", sessionId = "s-2", createdAt = from.plusSeconds(120)),
                    ),
                )

                val point =
                    queries
                        .timeSeries(property.domain, range(from = from, to = from.plusSeconds(3_600)), TimeGranularity.HOUR)
                        .single()

                point.pageViews shouldBe 1
                point.visitors shouldBe 2
            }

            test("returns zero-filled buckets for an unknown domain") {
                val points =
                    queries.timeSeries(
                        "missing.example",
                        range(from = amsterdamStartOfDayInstant(2026, 1, 9), to = amsterdamStartOfDayInstant(2026, 1, 11)),
                        TimeGranularity.DAY,
                    )

                points shouldHaveSize 2
                points.map { it.pageViews } shouldContainExactly listOf(0L, 0L)
                points.map { it.visitors } shouldContainExactly listOf(0L, 0L)
            }

            test("excludes events outside the half-open window") {
                val from = amsterdamStartOfDayInstant(2026, 1, 9)
                val to = amsterdamStartOfDayInstant(2026, 1, 10)

                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1", createdAt = from.minusSeconds(1)),
                        event(property, sessionId = "s-2", createdAt = to),
                    ),
                )

                queries.timeSeries(property.domain, range(from = from, to = to), TimeGranularity.DAY).single().pageViews shouldBe 0
            }
        }

        context("screenSizes") {
            test("aggregates raw dimensions, most viewed first") {
                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1", screenWidth = 1920, screenHeight = 1080),
                        event(property, sessionId = "s-2", screenWidth = 1920, screenHeight = 1080),
                        event(property, sessionId = "s-3", screenWidth = 1280, screenHeight = 720),
                        event(property, sessionId = "s-4"),
                    ),
                )

                val stats = queries.screenSizes(property.domain, range())

                stats shouldHaveSize 2
                stats[0].width shouldBe 1920
                stats[0].height shouldBe 1080
                stats[0].pageViews shouldBe 2
                stats[1].width shouldBe 1280
                stats[1].pageViews shouldBe 1
            }
        }

        context("userAgents") {
            test("aggregates user agents, most viewed first") {
                fixture.write(listOf(event(property, userAgent = "Firefox")))

                val stats = queries.userAgents(property.domain, range())

                stats shouldHaveSize 1
                stats.first().userAgent shouldBe "Firefox"
                stats.first().pageViews shouldBe 1
            }

            test("returns an empty list when there is no data") {
                queries.userAgents(property.domain, range()).shouldBeEmpty()
            }

            test("groups events without a user agent under a placeholder") {
                fixture.write(listOf(event(property, userAgent = null)))

                queries.userAgents(property.domain, range()).single().userAgent shouldBe "???"
            }
        }

        context("routes") {
            test("strips scheme and domain, most viewed route first") {
                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1", url = "https://${property.domain}/"),
                        event(property, sessionId = "s-2", url = "https://${property.domain}/"),
                        event(property, sessionId = "s-3", url = "https://${property.domain}/blog"),
                    ),
                )

                val stats = queries.routes(property.domain, range())

                stats shouldHaveSize 2
                stats.first().route shouldBe "/"
                stats.first().pageViews shouldBe 2
                stats.drop(1).first().route shouldBe "/blog"
            }
        }

        context("events") {
            test("counts occurrences per event name, including custom events") {
                fixture.write(
                    listOf(
                        event(property, name = EventNames.PAGE_VIEW, sessionId = "s-1"),
                        event(property, name = EventNames.PAGE_VIEW, sessionId = "s-2"),
                        event(property, name = "signup-click", sessionId = "s-1"),
                        event(property, name = "hover", sessionId = "s-1"),
                    ),
                )

                val stats = queries.events(property.domain, range())

                stats.map { it.eventName } shouldBe listOf("page-view", "hover", "signup-click")
                stats.first().occurrences shouldBe 2
                stats.first().visitors shouldBe 2
                stats.first { it.eventName == "signup-click" }.visitors shouldBe 1
            }
        }

        context("countries") {
            test("groups page views by country, defaulting to ZZ") {
                fixture.write(
                    listOf(
                        event(property, sessionId = "s-1", country = "NL"),
                        event(property, sessionId = "s-2", country = "NL"),
                        event(property, sessionId = "s-3", country = "DE"),
                        event(property, sessionId = "s-4"),
                    ),
                )

                val stats = queries.countries(property.domain, range())

                stats.map { it.country } shouldBe listOf("NL", "DE", "ZZ")
                stats.first().pageViews shouldBe 2
                stats.first().visitors shouldBe 2
            }
        }
    })
