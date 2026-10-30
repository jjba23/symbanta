/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeBuckets
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import java.time.Instant
import java.time.ZoneId

/**
 * How one dashboard window is answered: which consecutive slices come from the day rollups, from
 * the hour rollups, and from the raw event log.
 *
 * The slices are disjoint and cover the window in order, so a read can union them and aggregate
 * once. Which slices exist is decided by three facts:
 *
 * - Rollups are only valid up to [coveredThrough] (see `stats_rollup_state`), everything after it
 *   is still read from `events`.
 * - Rollup buckets are whole hours, so the part of the window before the first whole hour has no
 *   bucket and is read from `events` (a `LAST_24_HOURS` window starts at, say, 10:37).
 * - Whole local days have their own rollup grain; the partial day at either end uses hours.
 *
 * An empty slice is a range whose start equals its end, which no row matches and which keeps the
 * SQL a fixed shape.
 */
data class RollupWindow(
    val rawBefore: DateRange,
    val hourRows: DateRange,
    val dayRows: DateRange,
    val rawAfter: DateRange,
) {
    companion object {
        /**
         * @param wholeDays when false, day rows are not used at all (an hourly time series needs
         *   sub-day resolution, so the same window must be read from hour rows).
         */
        fun of(
            range: DateRange,
            coveredThrough: Instant?,
            zone: ZoneId,
            wholeDays: Boolean,
        ): RollupWindow =
            if (coveredThrough == null || !coveredThrough.isAfter(range.from)) {
                rawOnly(range)
            } else {
                split(range, coveredThrough, zone, wholeDays)
            }

        private fun split(
            range: DateRange,
            coveredThrough: Instant,
            zone: ZoneId,
            wholeDays: Boolean,
        ): RollupWindow {
            val firstWholeHour = TimeBuckets.ceilToHour(range.from)
            // A window that ends mid-hour in the past cannot be answered by that hour's rollup row,
            // which covers the whole hour: the tail of the window is read from the event log and the
            // rollups stop at the hour start. A window that ends at or after the coverage needs no
            // such cut, because the coverage itself already ends inside that hour.
            val alignedTo =
                if (range.to == TimeBuckets.floorToHour(range.to) || !range.to.isBefore(coveredThrough)) {
                    range.to
                } else {
                    TimeBuckets.floorToHour(range.to)
                }
            val coveredTo = minOf(coveredThrough, alignedTo)

            // A window shorter than the remaining part of its first hour, or one whose coverage
            // ends inside that hour, has no whole bucket at all.
            if (!firstWholeHour.isBefore(range.to) || !coveredTo.isAfter(firstWholeHour)) {
                return rawOnly(range)
            }

            // Empty slices stay where they would be, so the four ranges always tile the window.
            val rawBefore =
                if (firstWholeHour.isAfter(range.from)) {
                    DateRange(range.from, firstWholeHour)
                } else {
                    DateRange(range.from, range.from)
                }
            val rawAfter =
                if (coveredTo.isBefore(range.to)) DateRange(coveredTo, range.to) else DateRange(coveredTo, coveredTo)

            var dayFrom = firstWholeHour
            var dayTo = firstWholeHour
            if (wholeDays) {
                val firstMidnight = TimeBuckets.ceilToLocalMidnight(firstWholeHour, zone)
                val lastMidnight = TimeBuckets.floorToLocalMidnight(coveredTo, zone)
                if (lastMidnight.isAfter(firstMidnight)) {
                    dayFrom = firstMidnight
                    dayTo = lastMidnight
                }
            }

            return RollupWindow(
                rawBefore = rawBefore,
                hourRows = DateRange(firstWholeHour, coveredTo),
                dayRows = DateRange(dayFrom, dayTo),
                rawAfter = rawAfter,
            )
        }

        private fun rawOnly(range: DateRange): RollupWindow {
            val empty = DateRange(range.from, range.from)
            return RollupWindow(rawBefore = empty, hourRows = empty, dayRows = empty, rawAfter = range)
        }
    }
}
