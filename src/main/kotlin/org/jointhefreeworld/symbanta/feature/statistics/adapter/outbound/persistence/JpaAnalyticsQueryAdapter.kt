/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.EventJpaRepository
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.EventStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.MAX_TIME_SERIES_POINTS
import org.jointhefreeworld.symbanta.feature.statistics.domain.RouteStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.StatsSummary
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeSeriesPoint
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentStat
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.shared.domain.ReportingPeriodResolver
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Analytics read port implemented on top of Spring Data JPA.
 *
 * Property lookup goes through the properties port (the published contract of that feature), and
 * every aggregate is a JPQL query on [EventJpaRepository]; the domain's event window is resolved
 * once per call and an unknown domain yields zeroes/empty lists, exactly like the previous SQL.
 *
 * Time-series buckets are generated in the reporting time zone and merged with the grouped rows so
 * the result is zero-filled even for windows without traffic, and every bucket timestamp is the
 * local start of that hour/day (the same key the SQL truncation produces).
 */
open class JpaAnalyticsQueryAdapter(
    private val properties: PropertyRepositoryPort,
    private val events: EventJpaRepository,
) : AnalyticsQueryPort {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun summary(
        domain: String,
        range: DateRange,
    ): StatsSummary =
        withLoggingContext(
            "domain" to domain,
            "from" to range.from.toString(),
            "to" to range.to.toString(),
        ) {
            logger.debug { "Fetching analytics stats summary from database" }

            val property = properties.byDomain(domain) ?: return@withLoggingContext EMPTY_SUMMARY
            // Counts and session behaviour come from one pass; see the query's own comment.
            val counts = events.sessionSummary(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)

            StatsSummary(
                pageViews = counts?.pageViews ?: 0,
                visitors = counts?.visitors ?: 0,
                bounceRate = counts?.bounceRate ?: BigDecimal.ZERO,
                averageDurationMillis = BigDecimal.valueOf(counts?.avgDurationMs ?: 0.0),
            )
        }

    @Transactional(readOnly = true)
    override fun timeSeries(
        domain: String,
        range: DateRange,
        granularity: TimeGranularity,
    ): List<TimeSeriesPoint> {
        logger.debug { "Fetching time-series analytics stats from database" }

        val window = boundedWindow(range, granularity)
        val buckets = bucketsOf(window, granularity)
        val property = properties.byDomain(domain)

        val counts =
            if (property == null) {
                emptyMap()
            } else {
                events
                    .timeSeries(
                        property.id.value,
                        window.from,
                        window.to,
                        granularity.unit,
                        ReportingPeriodResolver.TIMEZONE,
                        EventNames.PAGE_VIEW,
                    ).associate { it.bucket to it }
            }

        return buckets.map { bucket ->
            val row = counts[bucket]
            TimeSeriesPoint(
                timestamp = bucket,
                pageViews = row?.pageViews ?: 0,
                visitors = row?.visitors ?: 0,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun screenSizes(
        domain: String,
        range: DateRange,
    ): List<ScreenSizeStat> {
        logger.debug { "Fetching screen sizes breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .screenSizes(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            .map { ScreenSizeStat(width = it.width, height = it.height, pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun userAgents(
        domain: String,
        range: DateRange,
    ): List<UserAgentStat> {
        logger.debug { "Fetching user agents breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .userAgents(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            .map { UserAgentStat(userAgent = it.userAgent ?: "???", pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun routes(
        domain: String,
        range: DateRange,
    ): List<RouteStat> {
        logger.debug { "Fetching routes breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .routes(property.id.value, domain, range.from, range.to, EventNames.PAGE_VIEW)
            .map { RouteStat(route = it.route ?: "???", pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun events(
        domain: String,
        range: DateRange,
    ): List<EventStat> {
        logger.debug { "Fetching event type breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .events(property.id.value, range.from, range.to)
            .map { EventStat(eventName = it.eventName, occurrences = it.occurrences, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun countries(
        domain: String,
        range: DateRange,
    ): List<CountryStat> {
        logger.debug { "Fetching country breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .countries(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            .map { CountryStat(country = it.country, pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    /**
     * Keeps a read proportional to what a chart can render: a window longer than
     * [MAX_TIME_SERIES_POINTS] buckets is served as its most recent buckets, so `ALL_TIME` neither
     * scans nor aggregates the entire history. Shorter windows are returned untouched.
     */
    private fun boundedWindow(
        range: DateRange,
        granularity: TimeGranularity,
    ): DateRange {
        var first = lastBucket(range, granularity)

        repeat(MAX_TIME_SERIES_POINTS - 1) {
            first = previousBucket(first, granularity)
        }

        return if (first.toInstant().isBefore(range.from)) range else DateRange(first.toInstant(), range.to)
    }

    /**
     * Start instants of every bucket that overlaps the window, in the reporting time zone.
     *
     * The window is half-open (`from` inclusive, `to` exclusive): a window that ends exactly on a
     * bucket boundary does not grow a permanently empty trailing point, while a window that ends
     * mid-bucket (`TODAY` ends at "now") still includes the bucket the current time falls in.
     */
    private fun bucketsOf(
        range: DateRange,
        granularity: TimeGranularity,
    ): List<Instant> {
        val first = bucketStart(range.from, ReportingPeriodResolver.ZONE_ID, granularity)
        val last = lastBucket(range, granularity)

        if (last.isBefore(first)) return emptyList()

        val buckets = mutableListOf<Instant>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            buckets += cursor.toInstant()
            cursor = nextBucket(cursor, granularity)
        }

        return buckets
    }

    /** The bucket containing `range.to`, or the one before it when the window ends exactly on a boundary. */
    private fun lastBucket(
        range: DateRange,
        granularity: TimeGranularity,
    ): ZonedDateTime {
        val endBucket = bucketStart(range.to, ReportingPeriodResolver.ZONE_ID, granularity)
        return if (endBucket.toInstant() == range.to) previousBucket(endBucket, granularity) else endBucket
    }

    private fun bucketStart(
        instant: Instant,
        zone: ZoneId,
        granularity: TimeGranularity,
    ): ZonedDateTime =
        when (granularity) {
            // Whole-hour offsets make wall-clock truncation safe; going through the local date for
            // days avoids inventing a midnight that does not exist on a DST transition.
            TimeGranularity.HOUR -> instant.atZone(zone).truncatedTo(ChronoUnit.HOURS)
            TimeGranularity.DAY -> instant.atZone(zone).toLocalDate().atStartOfDay(zone)
        }

    private fun nextBucket(
        bucket: ZonedDateTime,
        granularity: TimeGranularity,
    ): ZonedDateTime =
        when (granularity) {
            TimeGranularity.HOUR -> bucket.plusHours(1)
            TimeGranularity.DAY -> bucket.plusDays(1)
        }

    private fun previousBucket(
        bucket: ZonedDateTime,
        granularity: TimeGranularity,
    ): ZonedDateTime =
        when (granularity) {
            TimeGranularity.HOUR -> bucket.minusHours(1)
            TimeGranularity.DAY -> bucket.minusDays(1)
        }

    private companion object {
        val EMPTY_SUMMARY = StatsSummary(pageViews = 0, visitors = 0, bounceRate = BigDecimal.ZERO, averageDurationMillis = BigDecimal.ZERO)
    }
}
