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

package org.jointhefreeworld.symbanta.application.port.outbound

import org.jointhefreeworld.symbanta.domain.model.ConsoleLogEntry
import org.jointhefreeworld.symbanta.domain.model.ConsoleLogLevel
import org.jointhefreeworld.symbanta.domain.model.CountryStat
import org.jointhefreeworld.symbanta.domain.model.DateRange
import org.jointhefreeworld.symbanta.domain.model.EventStat
import org.jointhefreeworld.symbanta.domain.model.RouteStat
import org.jointhefreeworld.symbanta.domain.model.ScreenSizeStat
import org.jointhefreeworld.symbanta.domain.model.StatsSummary
import org.jointhefreeworld.symbanta.domain.model.TimeGranularity
import org.jointhefreeworld.symbanta.domain.model.TimeSeriesPoint
import org.jointhefreeworld.symbanta.domain.model.UserAgentStat

/**
 * Driven port: read side of the analytics store.
 *
 * Implementations may be relational (PostgreSQL today) or anything else; the application
 * layer only knows these domain-typed operations.
 */
interface AnalyticsQueryPort {
    fun summary(
        domain: String,
        range: DateRange,
    ): StatsSummary

    fun timeSeries(
        domain: String,
        range: DateRange,
        granularity: TimeGranularity,
    ): List<TimeSeriesPoint>

    fun screenSizes(
        domain: String,
        range: DateRange,
    ): List<ScreenSizeStat>

    fun userAgents(
        domain: String,
        range: DateRange,
    ): List<UserAgentStat>

    fun routes(
        domain: String,
        range: DateRange,
    ): List<RouteStat>

    fun events(
        domain: String,
        range: DateRange,
    ): List<EventStat>

    fun countries(
        domain: String,
        range: DateRange,
    ): List<CountryStat>

    fun consoleLogs(
        domain: String,
        level: ConsoleLogLevel?,
        limit: Int,
    ): List<ConsoleLogEntry>
}
