/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.domain

import org.jointhefreeworld.symbanta.shared.domain.DateRange
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Bucket arithmetic shared by every [org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort]
 * adapter.
 *
 * Chart buckets are wall-clock days and hours of the reporting time zone, while the rollup tables
 * store UTC-aligned hours plus local midnights; keeping both computations in one place is what
 * guarantees a rolled-up bucket lands in the same chart bucket as a raw event would.
 */
object TimeBuckets {
    /** Start of the bucket containing [instant], in the reporting time zone. */
    fun start(
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

    fun next(
        bucket: ZonedDateTime,
        granularity: TimeGranularity,
    ): ZonedDateTime =
        when (granularity) {
            TimeGranularity.HOUR -> bucket.plusHours(1)
            TimeGranularity.DAY -> bucket.plusDays(1)
        }

    fun previous(
        bucket: ZonedDateTime,
        granularity: TimeGranularity,
    ): ZonedDateTime =
        when (granularity) {
            TimeGranularity.HOUR -> bucket.minusHours(1)
            TimeGranularity.DAY -> bucket.minusDays(1)
        }

    /**
     * The bucket containing `range.to`, or the one before it when the window ends exactly on a
     * boundary, so a window ending at midnight does not grow a permanently empty trailing point.
     */
    fun lastBucket(
        range: DateRange,
        zone: ZoneId,
        granularity: TimeGranularity,
    ): ZonedDateTime {
        val endBucket = start(range.to, zone, granularity)
        return if (endBucket.toInstant() == range.to) previous(endBucket, granularity) else endBucket
    }

    /**
     * Start instants of every bucket that overlaps the window, in the reporting time zone.
     *
     * The window is half-open (`from` inclusive, `to` exclusive): a window that ends exactly on a
     * bucket boundary does not grow a permanently empty trailing point, while a window that ends
     * mid-bucket (`TODAY` ends at "now") still includes the bucket the current time falls in.
     */
    fun of(
        range: DateRange,
        zone: ZoneId,
        granularity: TimeGranularity,
    ): List<Instant> {
        val first = start(range.from, zone, granularity)
        val last = lastBucket(range, zone, granularity)

        if (last.isBefore(first)) return emptyList()

        val buckets = mutableListOf<Instant>()
        var cursor = first
        while (!cursor.isAfter(last)) {
            buckets += cursor.toInstant()
            cursor = next(cursor, granularity)
        }

        return buckets
    }

    /**
     * Keeps a read proportional to what a chart can render: a window longer than
     * [MAX_TIME_SERIES_POINTS] buckets is served as its most recent buckets, so `ALL_TIME` neither
     * scans nor aggregates the entire history. Shorter windows are returned untouched.
     */
    fun bounded(
        range: DateRange,
        zone: ZoneId,
        granularity: TimeGranularity,
    ): DateRange {
        var first = lastBucket(range, zone, granularity)

        repeat(MAX_TIME_SERIES_POINTS - 1) {
            first = previous(first, granularity)
        }

        return if (first.toInstant().isBefore(range.from)) range else DateRange(first.toInstant(), range.to)
    }

    /** Start of the UTC hour containing [instant]. */
    fun floorToHour(instant: Instant): Instant = instant.truncatedTo(ChronoUnit.HOURS)

    /** Start of the UTC hour containing [instant], or [instant] itself when it is already one. */
    fun ceilToHour(instant: Instant): Instant {
        val floor = floorToHour(instant)
        return if (floor == instant) instant else floor.plus(1, ChronoUnit.HOURS)
    }

    /** First local midnight at or after [instant]. */
    fun ceilToLocalMidnight(
        instant: Instant,
        zone: ZoneId,
    ): Instant {
        val startOfDay =
            instant
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
        return if (startOfDay == instant) instant else startOfDay.plus(1, ChronoUnit.DAYS).atZone(zone).toInstant()
    }

    /** Last local midnight at or before [instant]. */
    fun floorToLocalMidnight(
        instant: Instant,
        zone: ZoneId,
    ): Instant =
        instant
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
}
