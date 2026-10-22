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
