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
