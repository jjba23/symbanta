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

package org.jointhefreeworld.symbanta.application.port.inbound

import org.jointhefreeworld.symbanta.domain.model.CountryStat
import org.jointhefreeworld.symbanta.domain.model.EventStat
import org.jointhefreeworld.symbanta.domain.model.PeriodSelection
import org.jointhefreeworld.symbanta.domain.model.RouteStat
import org.jointhefreeworld.symbanta.domain.model.ScreenSizeGroupStat
import org.jointhefreeworld.symbanta.domain.model.StatsSummary
import org.jointhefreeworld.symbanta.domain.model.TimeGranularity
import org.jointhefreeworld.symbanta.domain.model.TimeSeriesPoint
import org.jointhefreeworld.symbanta.domain.model.UserAgentGroupStat
import java.time.Instant

/**
 * A statistics request: which application, and over which reporting period.
 *
 * The application service resolves the period against its clock, so every adapter gets the
 * same semantics without duplicating date math.
 */
data class StatsQuery(
    val domain: String,
    val period: PeriodSelection,
)

data class SummaryResult(
    val from: Instant,
    val to: Instant,
    val summary: StatsSummary,
)

data class TimeSeriesResult(
    val granularity: TimeGranularity,
    val from: Instant,
    val to: Instant,
    val points: List<TimeSeriesPoint>,
)

/** A resolved query window plus the breakdown it produced. */
data class BreakdownResult<T>(
    val from: Instant,
    val to: Instant,
    val items: List<T>,
)

interface GetStatsSummaryUseCase {
    fun summary(query: StatsQuery): SummaryResult
}

interface GetTimeSeriesUseCase {
    fun timeSeries(query: StatsQuery): TimeSeriesResult
}

interface GetRouteStatsUseCase {
    fun routes(query: StatsQuery): BreakdownResult<RouteStat>
}

interface GetScreenSizeStatsUseCase {
    fun screenSizes(query: StatsQuery): BreakdownResult<ScreenSizeGroupStat>
}

interface GetUserAgentStatsUseCase {
    fun userAgents(query: StatsQuery): BreakdownResult<UserAgentGroupStat>
}

/** Breakdown by event name, which is what makes custom/business events useful (#4). */
interface GetEventStatsUseCase {
    fun events(query: StatsQuery): BreakdownResult<EventStat>
}

/** Page views grouped by client country, stored since #7. */
interface GetCountryStatsUseCase {
    fun countries(query: StatsQuery): BreakdownResult<CountryStat>
}
