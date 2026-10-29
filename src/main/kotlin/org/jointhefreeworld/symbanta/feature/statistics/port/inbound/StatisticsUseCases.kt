/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.port.inbound

import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.EventStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.RouteStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeGroupStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.StatsSummary
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeSeriesPoint
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentGroupStat
import org.jointhefreeworld.symbanta.shared.domain.PeriodSelection
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
