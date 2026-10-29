/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetUserAgentStatsUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsUserAgentsController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1UserAgentStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1UserAgentStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsUserAgentsController(
    private val userAgents: GetUserAgentStatsUseCase,
) : V1StatsUserAgentsController {
    override fun getUserAgentStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1UserAgentStatsResponseDto> {
        val result = userAgents.userAgents(statsQuery(domain, period, startDate, endDate))

        return ResponseEntity.ok(
            V1UserAgentStatsResponseDto(
                from = result.from,
                to = result.to,
                items =
                    result.items.map {
                        V1UserAgentStatDto(
                            userAgent = it.label,
                            pageViews = it.pageViews.toInt(),
                            visitors = it.visitors.toInt(),
                        )
                    },
            ),
        )
    }
}
