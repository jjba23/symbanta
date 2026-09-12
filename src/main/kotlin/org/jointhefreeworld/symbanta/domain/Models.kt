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

package org.jointhefreeworld.symbanta.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.*

@JvmInline
value class PropertyId(
    val value: UUID,
)

@JvmInline
value class SessionId(
    val value: String,
)

@JvmInline
value class EventId(
    val value: UUID,
)

class Property(
    val id: PropertyId,
    val domain: String,
    val createdAt: Instant,
    val updatedAt: Instant?,
)

data class AnalyticsEvent(
    val id: EventId,
    val propertyId: PropertyId,
    val sessionId: SessionId?,
    val eventName: String,
    val url: String?,
    val referrer: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val language: String?,
    val userAgent: String?,
    val propsJson: String,
    val createdAt: Instant,
)

data class StatsSummary(
    val pageViews: Long,
    val visitors: Long,
    val bounceRate: BigDecimal,
    val averageDurationMillis: BigDecimal,
)

data class UserAgentStat(
    val userAgent: String,
    val pageViews: Long,
    val visitors: Long,
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
