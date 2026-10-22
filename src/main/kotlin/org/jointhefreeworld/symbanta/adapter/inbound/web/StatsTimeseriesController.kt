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

package org.jointhefreeworld.symbanta.adapter.inbound.web

import org.jointhefreeworld.symbanta.application.port.inbound.GetTimeSeriesUseCase
import org.jointhefreeworld.symbanta.domain.model.TimeGranularity
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsTimeseriesController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.TimeSeriesPointDto
import org.jointhefreeworld.symbanta.openapi.models.V1TimeSeriesStatsResponseDto
import org.jointhefreeworld.symbanta.openapi.models.V1TimeSeriesStatsResponseDtoGranularityDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsTimeseriesController(
    private val timeSeries: GetTimeSeriesUseCase,
) : V1StatsTimeseriesController {
    override fun getTimeSeriesStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1TimeSeriesStatsResponseDto> {
        val result = timeSeries.timeSeries(statsQuery(domain, period, startDate, endDate))

        val granularity =
            when (result.granularity) {
                TimeGranularity.HOUR -> V1TimeSeriesStatsResponseDtoGranularityDto.HOUR
                TimeGranularity.DAY -> V1TimeSeriesStatsResponseDtoGranularityDto.DAY
            }

        return ResponseEntity.ok(
            V1TimeSeriesStatsResponseDto(
                granularity = granularity,
                from = result.from,
                to = result.to,
                points =
                    result.points.map { point ->
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
