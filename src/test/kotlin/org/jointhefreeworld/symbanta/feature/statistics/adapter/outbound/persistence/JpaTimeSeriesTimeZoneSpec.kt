/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence
import java.time.Instant

/**
 * The dashboard window is resolved in Europe/Amsterdam but the connection's time zone belongs to
 * the PostgreSQL server, so truncating `timestamptz` directly produced bucket keys the adapter
 * never generated — every point came back zero. This pins the query to the reporting time zone
 * regardless of what the server (or a session) is configured with.
 */
class JpaTimeSeriesTimeZoneSpec :
    FunSpec({
        test("bucket keys do not depend on the database session time zone") {
            TestDatabase.migrate()
            TestDatabase.truncate()

            val property = AnalyticsFixtures.property("timezone.example")
            TestPersistence.propertyRepository.save(property)
            TestPersistence.eventStore.saveAll(
                listOf(AnalyticsFixtures.event(property, createdAt = Instant.parse("2026-01-10T12:00:00Z"))),
            )

            val range =
                DateRange(
                    from = amsterdamStartOfDayInstant(2026, 1, 9),
                    to = amsterdamStartOfDayInstant(2026, 1, 11),
                )

            withDatabaseTimeZone("Europe/Amsterdam") {
                val points = TestPersistence.analytics.timeSeries(property.domain, range, TimeGranularity.DAY)

                points.map { it.timestamp } shouldContainExactly
                    listOf(Instant.parse("2026-01-08T23:00:00Z"), Instant.parse("2026-01-09T23:00:00Z"))
                points.map { it.pageViews } shouldContainExactly listOf(0L, 1L)
            }
        }
    })

/**
 * Changes the database default for new sessions only (the suite's datasource does not pool, so the
 * adapter's next connection picks it up) and always restores it.
 */
private fun withDatabaseTimeZone(
    zone: String,
    block: () -> Unit,
) {
    val jdbc = TestDatabase.jdbcTemplate.jdbcTemplate
    val database = jdbc.queryForObject("select current_database()", String::class.java)
    jdbc.execute("ALTER DATABASE \"$database\" SET timezone TO '$zone'")

    try {
        block()
    } finally {
        jdbc.execute("ALTER DATABASE \"$database\" RESET timezone")
    }
}
