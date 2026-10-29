/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.domain

import java.math.BigDecimal
import java.time.Instant

data class StatsSummary(
    val pageViews: Long,
    val visitors: Long,
    val bounceRate: BigDecimal,
    val averageDurationMillis: BigDecimal,
)

enum class TimeGranularity(
    val unit: String,
    val intervalStep: String,
) {
    HOUR("hour", "1 hour"),
    DAY("day", "1 day"),
}

data class TimeSeriesPoint(
    val timestamp: Instant,
    val pageViews: Long,
    val visitors: Long,
)

/**
 * Upper bound on the number of buckets a time series may return (500 daily buckets, roughly
 * sixteen months).
 *
 * Windows longer than this — `ALL_TIME` starts at the epoch — are served as their most recent
 * buckets: a chart cannot use more points, and aggregating every event ever recorded would make
 * the query cost grow with the lifetime of the deployment.
 */
const val MAX_TIME_SERIES_POINTS = 500

/** Raw (width, height) bucket as stored in the database. */
data class ScreenSizeStat(
    val width: Int,
    val height: Int,
    val pageViews: Long,
    val visitors: Long,
)

/** Screen size aggregated into a human readable resolution class. */
data class ScreenSizeGroupStat(
    val size: String,
    val pageViews: Long,
    val visitors: Long,
)

data class UserAgentStat(
    val userAgent: String,
    val pageViews: Long,
    val visitors: Long,
)

/** User agent aggregated into a readable platform/browser label. */
data class UserAgentGroupStat(
    val label: String,
    val pageViews: Long,
    val visitors: Long,
)

data class RouteStat(
    val route: String,
    val pageViews: Long,
    val visitors: Long,
)

/** Occurrences of a named (custom) event type within the requested window. */
data class EventStat(
    val eventName: String,
    val occurrences: Long,
    val visitors: Long,
)

/** Page views and visitors grouped by the (coarse) country of the client. */
data class CountryStat(
    val country: String,
    val pageViews: Long,
    val visitors: Long,
)
