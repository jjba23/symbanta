/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetEventStatsUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsEventsController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1EventStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1EventStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsEventsController(
    private val events: GetEventStatsUseCase,
) : V1StatsEventsController {
    override fun getEventStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1EventStatsResponseDto> {
        val result = events.events(statsQuery(domain, period, startDate, endDate))

        return ResponseEntity.ok(
            V1EventStatsResponseDto(
                from = result.from,
                to = result.to,
                items =
                    result.items.map {
                        V1EventStatDto(
                            eventName = it.eventName,
                            occurrences = it.occurrences.toInt(),
                            visitors = it.visitors.toInt(),
                        )
                    },
            ),
        )
    }
}
