/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetTimeSeriesUseCase
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
