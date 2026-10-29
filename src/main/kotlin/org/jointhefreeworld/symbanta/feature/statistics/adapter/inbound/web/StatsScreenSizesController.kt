/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetScreenSizeStatsUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsScreenSizesController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1ScreenSizeStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1ScreenSizeStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsScreenSizesController(
    private val screenSizes: GetScreenSizeStatsUseCase,
) : V1StatsScreenSizesController {
    override fun getScreenSizeStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1ScreenSizeStatsResponseDto> {
        val result = screenSizes.screenSizes(statsQuery(domain, period, startDate, endDate))

        return ResponseEntity.ok(
            V1ScreenSizeStatsResponseDto(
                from = result.from,
                to = result.to,
                items =
                    result.items.map {
                        V1ScreenSizeStatDto(
                            size = it.size,
                            pageViews = it.pageViews.toInt(),
                            visitors = it.visitors.toInt(),
                        )
                    },
            ),
        )
    }
}
