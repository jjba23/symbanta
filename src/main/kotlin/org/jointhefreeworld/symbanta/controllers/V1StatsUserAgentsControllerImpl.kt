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

package org.jointhefreeworld.symbanta.controllers

import org.jointhefreeworld.symbanta.UserAgentParser
import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsUserAgentsController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1UserAgentStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1UserAgentStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate

@RestController
class V1StatsUserAgentsControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : V1StatsUserAgentsController {
    override fun getUserAgentStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1UserAgentStatsResponseDto> {
        val (from, to) = PeriodResolver.resolve(period, startDate, endDate, clock)

        val stats =
            analyticsRepository.getUserAgentStats(
                domain = domain,
                from = from,
                to = to,
            )

        val aggregatedItems =
            stats
                .groupBy { UserAgentParser.parse(it.userAgent) }
                .map { (parsedLabel, group) ->
                    V1UserAgentStatDto(
                        userAgent = parsedLabel,
                        pageViews = group.sumOf { it.pageViews.toInt() },
                        visitors = group.sumOf { it.visitors.toInt() },
                    )
                }.sortedByDescending { it.pageViews }
        return ResponseEntity.ok(
            V1UserAgentStatsResponseDto(
                from = from,
                to = to,
                items = aggregatedItems,
            ),
        )
    }
}
