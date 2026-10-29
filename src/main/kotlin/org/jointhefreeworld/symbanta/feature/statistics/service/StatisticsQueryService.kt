/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.service

import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.EventStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.RouteStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeGroupStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeMapper
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentGroupStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentParser
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.BreakdownResult
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetCountryStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetEventStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetRouteStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetScreenSizeStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetStatsSummaryUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetTimeSeriesUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetUserAgentStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.SummaryResult
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.TimeSeriesResult
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.shared.domain.ReportingPeriodResolver
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort

/**
 * Single implementation of every read-side use case: resolves the reporting period once,
 * applies the domain aggregations and never leaks storage details upwards.
 */
class StatisticsQueryService(
    private val queries: AnalyticsQueryPort,
    private val clock: ClockPort,
) : GetStatsSummaryUseCase,
    GetTimeSeriesUseCase,
    GetRouteStatsUseCase,
    GetScreenSizeStatsUseCase,
    GetUserAgentStatsUseCase,
    GetEventStatsUseCase,
    GetCountryStatsUseCase {
    override fun summary(query: StatsQuery): SummaryResult {
        val range = resolve(query)
        return SummaryResult(
            from = range.from,
            to = range.to,
            summary = queries.summary(query.domain, range),
        )
    }

    override fun timeSeries(query: StatsQuery): TimeSeriesResult {
        val range = resolve(query)
        val granularity = ReportingPeriodResolver.granularity(query.period.period, range)
        val points = queries.timeSeries(query.domain, range, granularity)

        return TimeSeriesResult(
            granularity = granularity,
            // The adapter bounds very long windows (ALL_TIME) to their most recent buckets, so the
            // reported window start is the first bucket actually returned.
            from = points.firstOrNull()?.timestamp ?: range.from,
            to = range.to,
            points = points,
        )
    }

    override fun routes(query: StatsQuery): BreakdownResult<RouteStat> {
        val range = resolve(query)
        return breakdown(range, queries.routes(query.domain, range))
    }

    override fun screenSizes(query: StatsQuery): BreakdownResult<ScreenSizeGroupStat> {
        val range = resolve(query)
        return breakdown(
            range,
            ScreenSizeMapper.aggregate(queries.screenSizes(query.domain, range)),
        )
    }

    override fun userAgents(query: StatsQuery): BreakdownResult<UserAgentGroupStat> {
        val range = resolve(query)
        return breakdown(
            range,
            UserAgentParser.aggregate(queries.userAgents(query.domain, range)),
        )
    }

    override fun events(query: StatsQuery): BreakdownResult<EventStat> {
        val range = resolve(query)
        return breakdown(range, queries.events(query.domain, range))
    }

    override fun countries(query: StatsQuery): BreakdownResult<CountryStat> {
        val range = resolve(query)
        return breakdown(range, queries.countries(query.domain, range))
    }

    private fun resolve(query: StatsQuery): DateRange =
        ReportingPeriodResolver.resolve(
            period = query.period.period,
            startDate = query.period.startDate,
            endDate = query.period.endDate,
            now = clock.now(),
        )

    private fun <T> breakdown(
        range: DateRange,
        items: List<T>,
    ): BreakdownResult<T> = BreakdownResult(from = range.from, to = range.to, items = items)
}
