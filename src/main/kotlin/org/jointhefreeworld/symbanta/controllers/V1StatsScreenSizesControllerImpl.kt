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

import org.jointhefreeworld.symbanta.ScreenSizeMapper
import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsScreenSizesController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1ScreenSizeStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1ScreenSizeStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate

@RestController
class V1StatsScreenSizesControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : V1StatsScreenSizesController {
    override fun getScreenSizeStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1ScreenSizeStatsResponseDto> {
        val (from, to) = PeriodResolver.resolve(period, startDate, endDate, clock)

        val rawStats =
            analyticsRepository.getScreenSizeStats(
                domain = domain,
                from = from,
                to = to,
            )

        val aggregatedItems =
            ScreenSizeMapper.aggregate(rawStats).map { stat ->
                V1ScreenSizeStatDto(
                    width = stat.width,
                    height = stat.height,
                    pageViews = stat.pageViews.toInt(),
                    visitors = stat.visitors.toInt(),
                )
            }

        return ResponseEntity.ok(
            V1ScreenSizeStatsResponseDto(
                from = from,
                to = to,
                items = aggregatedItems,
            ),
        )
    }
}
