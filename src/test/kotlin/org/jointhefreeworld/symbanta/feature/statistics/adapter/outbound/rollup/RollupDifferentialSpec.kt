/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.random.Random

/**
 * Holds the two analytics adapters against each other on a messy dataset.
 *
 * The rollup tables are a denormalisation, so the risk is not that a query fails but that it
 * quietly answers a slightly different question: a tab that spans two hours counted twice, a
 * session crossing local midnight losing its active time, a day boundary double counting. Every
 * window here is answered by both the direct-from-events adapter and the rollup one, and the
 * answers have to match.
 */
class RollupDifferentialSpec :
    FunSpec({
        test("a window on whole local days agrees") {
            val property = seeded()

            assertSameAnswers(property.domain, DateRange(local(2026, 1, 6), local(2026, 1, 9)))
        }

        test("a window starting and ending mid-hour agrees") {
            val property = seeded()

            assertSameAnswers(property.domain, DateRange(local(2026, 1, 7, 10, 37), local(2026, 1, 10, 15, 12)))
        }

        test("a single local day agrees") {
            val property = seeded()

            assertSameAnswers(property.domain, DateRange(local(2026, 1, 8), local(2026, 1, 9)))
        }

        test("a single hour agrees") {
            val property = seeded()

            assertSameAnswers(property.domain, DateRange(local(2026, 1, 8, 10), local(2026, 1, 8, 11)))
        }

        test("a window crossing local midnight agrees") {
            val property = seeded()

            assertSameAnswers(property.domain, DateRange(local(2026, 1, 8, 22), local(2026, 1, 9, 2)))
        }

        test("a whole week agrees at both chart granularities") {
            val property = seeded()

            assertSameAnswers(property.domain, DateRange(local(2026, 1, 5), local(2026, 1, 12)))
        }

        test("events newer than the last rollup run are still counted") {
            val property = seeded()
            // Ingested now, i.e. after the rollups ran: exactly the slice they do not cover.
            val tail = Instant.now()

            TestPersistence.eventStore.saveAll(
                listOf(
                    AnalyticsFixtures.event(property, sessionId = "tail-1", createdAt = tail),
                    AnalyticsFixtures.event(property, sessionId = "tail-1", createdAt = tail.plusSeconds(5), name = "signup-click"),
                    AnalyticsFixtures.event(property, sessionId = "tail-2", createdAt = tail.plusSeconds(10)),
                ),
            )

            assertSameAnswers(property.domain, DateRange(tail.minusSeconds(3_600), tail.plusSeconds(60)))
        }

        test("a covered window is answered without the event log") {
            val property = seeded()
            val range = DateRange(local(2026, 1, 6), local(2026, 1, 10))

            val summary = TestPersistence.rollupAnalytics.summary(property.domain, range)
            val routes = TestPersistence.rollupAnalytics.routes(property.domain, range)
            val series =
                TestPersistence.rollupAnalytics.timeSeries(property.domain, range, TimeGranularity.DAY)

            TestDatabase.jdbcTemplate.update("DELETE FROM events", emptyMap<String, Any>())

            TestPersistence.rollupAnalytics.summary(property.domain, range) shouldBe summary
            TestPersistence.rollupAnalytics.routes(property.domain, range) shouldBe routes
            TestPersistence.rollupAnalytics.timeSeries(property.domain, range, TimeGranularity.DAY) shouldBe series
            // The window has traffic, so a zero here would mean the check passed vacuously.
            (summary.pageViews > 0) shouldBe true
            routes.isEmpty() shouldBe false
        }
    })

private val zone = ZoneId.of("Europe/Amsterdam")

private fun local(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 0,
    minute: Int = 0,
): Instant = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

/** A fresh property with a deterministic, deliberately awkward week of traffic, rolled up. */
private fun seeded(): Property {
    TestDatabase.migrate()
    TestDatabase.truncate()

    val property = AnalyticsFixtures.property("diff.example")
    TestPersistence.propertyRepository.save(property)
    TestPersistence.eventStore.saveAll(dataset(property))
    TestPersistence.rollupRefresher.refresh()
    return property
}

private fun dataset(property: Property): List<AnalyticsEvent> {
    val random = Random(20260110)
    val events = mutableListOf<AnalyticsEvent>()
    val start = Instant.parse("2026-01-05T06:00:00Z")
    val names = listOf(EventNames.PAGE_VIEW, EventNames.PAGE_VIEW, EventNames.PAGE_VIEW, "signup-click", "hover")
    val urls =
        listOf(
            null,
            "https://diff.example/",
            "https://diff.example/blog",
            "https://diff.example/pricing?ref=feed",
        )
    val countries = listOf(null, "NL", "DE", "US")
    val agents = listOf(null, "Mozilla/5.0 Firefox/121", "Mozilla/5.0 Chrome/120", "Mozilla/5.0 Safari/17")
    val screens = listOf(null, 1920 to 1080, 1280 to 720, 390 to 844)

    repeat(120) { index ->
        var at = start.plusSeconds(random.nextLong(0, 6 * 86_400L))
        repeat(random.nextInt(1, 7)) {
            val screen = screens[random.nextInt(screens.size)]
            events +=
                AnalyticsFixtures.event(
                    property = property,
                    name = names[random.nextInt(names.size)],
                    sessionId = if (index % 23 == 0) null else "s-$index",
                    url = urls[random.nextInt(urls.size)],
                    country = countries[random.nextInt(countries.size)],
                    userAgent = agents[random.nextInt(agents.size)],
                    screenWidth = screen?.first,
                    screenHeight = screen?.second,
                    createdAt = at,
                )
            // Some gaps are longer than the 30 second activity window on purpose.
            at = at.plusSeconds(random.nextLong(0, 45))
        }
    }

    events +=
        listOf(
            // One tab across two hours: its visitors must not be counted as two.
            AnalyticsFixtures.event(property, sessionId = "span-hours", createdAt = Instant.parse("2026-01-08T09:10:00Z")),
            AnalyticsFixtures.event(property, sessionId = "span-hours", createdAt = Instant.parse("2026-01-08T10:40:00Z")),
            // One tab across local midnight with a 20 second gap: active time spans the day rollup.
            AnalyticsFixtures.event(property, sessionId = "span-midnight", createdAt = Instant.parse("2026-01-08T22:59:50Z")),
            AnalyticsFixtures.event(
                property,
                sessionId = "span-midnight",
                createdAt = Instant.parse("2026-01-08T23:00:10Z"),
                name = "signup-click",
            ),
        )

    return events
}

private fun assertSameAnswers(
    domain: String,
    range: DateRange,
) {
    val direct = TestPersistence.analytics
    val rolled = TestPersistence.rollupAnalytics

    val expected = direct.summary(domain, range)
    val actual = rolled.summary(domain, range)
    withClue(range, "summary pageViews") { actual.pageViews shouldBe expected.pageViews }
    withClue(range, "summary visitors") { actual.visitors shouldBe expected.visitors }
    withClue(range, "summary bounceRate") { actual.bounceRate.compareTo(expected.bounceRate) shouldBe 0 }
    withClue(range, "summary averageDurationMillis") {
        abs(actual.averageDurationMillis.toDouble() - expected.averageDurationMillis.toDouble()) shouldBeLessThan 0.001
    }

    withClue(range, "hourly time series") {
        rolled.timeSeries(domain, range, TimeGranularity.HOUR) shouldBe
            direct.timeSeries(domain, range, TimeGranularity.HOUR)
    }
    withClue(range, "daily time series") {
        rolled.timeSeries(domain, range, TimeGranularity.DAY) shouldBe
            direct.timeSeries(domain, range, TimeGranularity.DAY)
    }

    withClue(range, "routes") {
        rolled.routes(domain, range).associate { it.route to (it.pageViews to it.visitors) } shouldBe
            direct.routes(domain, range).associate { it.route to (it.pageViews to it.visitors) }
    }
    withClue(range, "countries") {
        rolled.countries(domain, range).associate { it.country to (it.pageViews to it.visitors) } shouldBe
            direct.countries(domain, range).associate { it.country to (it.pageViews to it.visitors) }
    }
    withClue(range, "screen sizes") {
        rolled.screenSizes(domain, range).associate { (it.width to it.height) to (it.pageViews to it.visitors) } shouldBe
            direct.screenSizes(domain, range).associate { (it.width to it.height) to (it.pageViews to it.visitors) }
    }
    withClue(range, "user agents") {
        rolled.userAgents(domain, range).associate { it.userAgent to (it.pageViews to it.visitors) } shouldBe
            direct.userAgents(domain, range).associate { it.userAgent to (it.pageViews to it.visitors) }
    }
    withClue(range, "event names") {
        rolled.events(domain, range).associate { it.eventName to (it.occurrences to it.visitors) } shouldBe
            direct.events(domain, range).associate { it.eventName to (it.occurrences to it.visitors) }
    }
}

private fun withClue(
    range: DateRange,
    what: String,
    assertion: () -> Unit,
) {
    try {
        assertion()
    } catch (error: AssertionError) {
        throw AssertionError("$what differs for $range: ${error.message}", error)
    }
}
