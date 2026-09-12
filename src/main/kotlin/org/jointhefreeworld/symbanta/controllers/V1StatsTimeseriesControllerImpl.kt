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

import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.domain.TimeGranularity
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsTimeseriesController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.TimeSeriesPointDto
import org.jointhefreeworld.symbanta.openapi.models.V1TimeSeriesStatsResponseDto
import org.jointhefreeworld.symbanta.openapi.models.V1TimeSeriesStatsResponseDtoGranularityDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate

@RestController
class V1StatsTimeseriesControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : V1StatsTimeseriesController {
    override fun getTimeSeriesStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1TimeSeriesStatsResponseDto> {
        val (from, to) = PeriodResolver.resolve(period, startDate, endDate, clock)
        val granularity = PeriodResolver.resolveGranularity(period, from, to)

        val points =
            analyticsRepository.getTimeSeriesStats(
                domain = domain,
                from = from,
                to = to,
                granularity = granularity,
            )

        val dtoGranularity =
            when (granularity) {
                TimeGranularity.HOUR -> V1TimeSeriesStatsResponseDtoGranularityDto.HOUR
                TimeGranularity.DAY -> V1TimeSeriesStatsResponseDtoGranularityDto.DAY
            }

        return ResponseEntity.ok(
            V1TimeSeriesStatsResponseDto(
                granularity = dtoGranularity,
                from = from,
                to = to,
                points =
                    points.map { point ->
                        TimeSeriesPointDto(
                            timestamp = point.timestamp,
                            pageViews = point.pageViews.toInt(),
                            visitors = point.visitors.toInt(),
                        )
                    },
            ),
        )
    }
}
