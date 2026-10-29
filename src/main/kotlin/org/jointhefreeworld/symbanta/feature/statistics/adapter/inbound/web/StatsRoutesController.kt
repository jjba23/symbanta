/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetRouteStatsUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsRoutesController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1RouteStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1RouteStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsRoutesController(
    private val routes: GetRouteStatsUseCase,
) : V1StatsRoutesController {
    override fun getRouteStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1RouteStatsResponseDto> {
        val result = routes.routes(statsQuery(domain, period, startDate, endDate))

        return ResponseEntity.ok(
            V1RouteStatsResponseDto(
                from = result.from,
                to = result.to,
                items =
                    result.items.map {
                        V1RouteStatDto(
                            route = it.route,
                            pageViews = it.pageViews.toInt(),
                            visitors = it.visitors.toInt(),
                        )
                    },
            ),
        )
    }
}
