/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence
import org.springframework.jdbc.core.RowMapper
import java.time.Instant
import java.time.OffsetDateTime

/**
 * The bucket contents themselves: what one rollup row means, and how the watermark moves.
 *
 * Reads are covered by the port contract and the differential spec; these tests pin the pieces a
 * read cannot show — that a tab across an hour is split into two rows, that its active time is
 * stitched back together at day grain, and that the watermark never runs ahead of the rows.
 */
class StatsRollupRefresherSpec :
    FunSpec({
        lateinit var property: Property

        beforeTest {
            TestDatabase.migrate()
            TestDatabase.truncate()
            property = AnalyticsFixtures.property("rollup.example")
            TestPersistence.propertyRepository.save(property)
        }

        fun seed(vararg events: AnalyticsEvent) {
            TestPersistence.eventStore.saveAll(events.toList())
        }

        test("writes one row per session per hour with its page views") {
            seed(
                AnalyticsFixtures.event(property, sessionId = "s-1", createdAt = Instant.parse("2026-01-08T09:10:00Z")),
                AnalyticsFixtures.event(property, sessionId = "s-1", createdAt = Instant.parse("2026-01-08T09:20:00Z")),
                AnalyticsFixtures.event(property, sessionId = "s-1", createdAt = Instant.parse("2026-01-08T10:05:00Z")),
            )

            TestPersistence.rollupRefresher.refresh()

            hourRows("s-1") shouldBe listOf(Triple("2026-01-08T09:00:00Z", 2L, 0L), Triple("2026-01-08T10:00:00Z", 1L, 0L))
        }

        test("keeps active time of a gap inside a bucket, and stitches a gap across buckets at day grain") {
            seed(
                AnalyticsFixtures.event(property, sessionId = "cross", createdAt = Instant.parse("2026-01-08T09:59:50Z")),
                AnalyticsFixtures.event(property, sessionId = "cross", createdAt = Instant.parse("2026-01-08T10:00:10Z")),
            )

            TestPersistence.rollupRefresher.refresh()

            // Each hour holds a single event, so neither can see the 20 second gap ...
            hourRows("cross") shouldBe
                listOf(Triple("2026-01-08T09:00:00Z", 1L, 0L), Triple("2026-01-08T10:00:00Z", 1L, 0L))
            // ... the day row solders the two hours together.
            scalar("SELECT active_us FROM stats_session_bucket WHERE bucket_width = 'day' AND session_id = 'cross'") shouldBe
                20_000_000L
        }

        test("keeps long gaps out of the active time") {
            seed(
                AnalyticsFixtures.event(property, sessionId = "idle", createdAt = Instant.parse("2026-01-08T09:00:00Z")),
                AnalyticsFixtures.event(property, sessionId = "idle", createdAt = Instant.parse("2026-01-08T09:05:00Z")),
                AnalyticsFixtures.event(property, sessionId = "idle", createdAt = Instant.parse("2026-01-08T09:05:10Z")),
            )

            TestPersistence.rollupRefresher.refresh()

            hourRows("idle") shouldBe listOf(Triple("2026-01-08T09:00:00Z", 3L, 10_000_000L))
        }

        test("groups events without a session under the empty session") {
            seed(AnalyticsFixtures.event(property, sessionId = null, createdAt = Instant.parse("2026-01-08T09:00:00Z")))

            TestPersistence.rollupRefresher.refresh()

            scalar("SELECT events FROM stats_session_bucket WHERE bucket_width = 'hour' AND session_id = ''") shouldBe 1L
        }

        test("records breakdown rows for every dimension") {
            seed(
                AnalyticsFixtures.event(
                    property,
                    sessionId = "s-1",
                    createdAt = Instant.parse("2026-01-08T09:00:00Z"),
                    url = "https://rollup.example/blog",
                    country = "NL",
                    screenWidth = 1920,
                    screenHeight = 1080,
                    userAgent = "Firefox",
                ),
                AnalyticsFixtures.event(
                    property,
                    sessionId = "s-1",
                    createdAt = Instant.parse("2026-01-08T09:01:00Z"),
                    name = "signup-click",
                    url = "https://rollup.example/blog",
                    country = "NL",
                    userAgent = "Firefox",
                ),
            )

            TestPersistence.rollupRefresher.refresh()

            scalar("SELECT count(*) FROM stats_dimension_bucket WHERE bucket_width = 'hour' AND dimension = 'route'") shouldBe 1L
            scalar(
                "SELECT page_views FROM stats_dimension_bucket " +
                    "WHERE bucket_width = 'hour' AND dimension = 'route' AND value = '/blog'",
            ) shouldBe 1L
            scalar(
                "SELECT events FROM stats_dimension_bucket " +
                    "WHERE bucket_width = 'hour' AND dimension = 'route' AND value = '/blog'",
            ) shouldBe 2L
            scalar(
                "SELECT events FROM stats_dimension_bucket " +
                    "WHERE bucket_width = 'hour' AND dimension = 'event' AND value = 'signup-click'",
            ) shouldBe 1L
            scalar(
                "SELECT events FROM stats_dimension_bucket " +
                    "WHERE bucket_width = 'hour' AND dimension = 'user_agent' AND value = 'Firefox'",
            ) shouldBe 2L
        }

        test("advances the watermark and picks up events written since the last run") {
            TestPersistence.rollupRefresher.coveredThrough() shouldBe null

            seed(AnalyticsFixtures.event(property, sessionId = "s-1", createdAt = Instant.parse("2026-01-08T09:00:00Z")))
            val first = TestPersistence.rollupRefresher.refresh()

            first shouldNotBe null
            first.isAfter(Instant.parse("2026-01-08T09:00:00Z")) shouldBe true

            val late = Instant.now().minusSeconds(5)
            seed(AnalyticsFixtures.event(property, sessionId = "late", createdAt = late))
            val second = TestPersistence.rollupRefresher.refresh()

            second.isAfter(first) shouldBe true
            scalar("SELECT count(*) FROM stats_session_bucket WHERE bucket_width = 'hour' AND session_id = 'late'") shouldBe 1L
        }

        test("works on a deployment with no events yet") {
            val covered = TestPersistence.rollupRefresher.refresh()

            covered shouldNotBe null
            TestPersistence.rollupRefresher.coveredThrough() shouldBe covered
            scalar("SELECT count(*) FROM stats_session_bucket") shouldBe 0L
        }

        test("keeps the watermark monotonic across repeated runs") {
            seed(AnalyticsFixtures.event(property, sessionId = "s-1", createdAt = Instant.parse("2026-01-08T09:00:00Z")))

            var previous = TestPersistence.rollupRefresher.refresh()
            repeat(3) {
                val next = TestPersistence.rollupRefresher.refresh()
                next.isBefore(previous) shouldBe false
                previous = next
            }
        }
    })

/** Hour rows of one session as (bucket, page views, active microseconds). */
private fun hourRows(sessionId: String): List<Triple<String, Long, Long>> =
    TestDatabase.jdbcTemplate.query(
        """
        SELECT bucket, page_views, active_us
        FROM stats_session_bucket
        WHERE bucket_width = 'hour' AND session_id = :sessionId
        ORDER BY bucket
        """.trimIndent(),
        mapOf("sessionId" to sessionId),
        RowMapper { rs, _ ->
            Triple(
                rs.getObject(1, OffsetDateTime::class.java).toInstant().toString(),
                rs.getLong(2),
                rs.getLong(3),
            )
        },
    )

private fun scalar(sql: String): Any? = TestDatabase.jdbcTemplate.queryForObject(sql, emptyMap<String, Any>(), Any::class.java)
