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

package org.jointhefreeworld.symbanta.application.service

import org.jointhefreeworld.symbanta.application.port.inbound.BreakdownResult
import org.jointhefreeworld.symbanta.application.port.inbound.GetCountryStatsUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.GetEventStatsUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.GetRouteStatsUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.GetScreenSizeStatsUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.GetStatsSummaryUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.GetTimeSeriesUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.GetUserAgentStatsUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.application.port.inbound.SummaryResult
import org.jointhefreeworld.symbanta.application.port.inbound.TimeSeriesResult
import org.jointhefreeworld.symbanta.application.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.domain.model.CountryStat
import org.jointhefreeworld.symbanta.domain.model.DateRange
import org.jointhefreeworld.symbanta.domain.model.EventStat
import org.jointhefreeworld.symbanta.domain.model.RouteStat
import org.jointhefreeworld.symbanta.domain.model.ScreenSizeGroupStat
import org.jointhefreeworld.symbanta.domain.model.UserAgentGroupStat
import org.jointhefreeworld.symbanta.domain.service.ReportingPeriodResolver
import org.jointhefreeworld.symbanta.domain.service.ScreenSizeMapper
import org.jointhefreeworld.symbanta.domain.service.UserAgentParser

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

        return TimeSeriesResult(
            granularity = granularity,
            from = range.from,
            to = range.to,
            points = queries.timeSeries(query.domain, range, granularity),
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
