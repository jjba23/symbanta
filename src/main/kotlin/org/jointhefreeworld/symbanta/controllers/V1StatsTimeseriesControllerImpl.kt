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
import org.jointhefreeworld.symbanta.openapi.models.PeriodDtoExtra
import org.jointhefreeworld.symbanta.openapi.models.TimeSeriesPointDto
import org.jointhefreeworld.symbanta.openapi.models.V1TimeSeriesStatsResponseDto
import org.jointhefreeworld.symbanta.openapi.models.V1TimeSeriesStatsResponseDtoGranularityDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.*

@RestController
class V1StatsTimeseriesControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : V1StatsTimeseriesController {
    override fun getTimeSeriesStats(
        domain: String,
        period: PeriodDtoExtra,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1TimeSeriesStatsResponseDto> {
        val now = clock.instant()
        val zoneId = ZoneId.of(TIMEZONE)

        val (from, to) = resolveTimeRange(period, startDate, endDate, now, zoneId)
        val granularity = resolveGranularity(period, from, to)

        val points =
            analyticsRepository.getTimeSeriesStats(
                domain = domain,
                from = from,
                to = to,
                granularity = granularity,
            )

        val response =
            V1TimeSeriesStatsResponseDto(
                granularity = V1TimeSeriesStatsResponseDtoGranularityDto.DAY,
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
            )

        return ResponseEntity.ok(response)
    }

    private fun resolveTimeRange(
        period: PeriodDtoExtra,
        startDate: LocalDate?,
        endDate: LocalDate?,
        now: Instant,
        zoneId: ZoneId,
    ): Pair<Instant, Instant> {
        val zdt = now.atZone(zoneId)

        val from: Instant =
            if (period == PeriodDtoExtra.CUSTOM) {
                checkNotNull(startDate).atStartOfDay(zoneId).toInstant()
            } else {
                when (period) {
                    PeriodDtoExtra.LAST_24_HOURS -> zdt.minusHours(24).toInstant()
                    PeriodDtoExtra.LAST_7_DAYS -> zdt.minusDays(7).toInstant()
                    PeriodDtoExtra.LAST_30_DAYS -> zdt.minusDays(30).toInstant()
                    PeriodDtoExtra.LAST_6_MONTHS -> zdt.minusMonths(6).toInstant()
                    PeriodDtoExtra.LAST_12_MONTHS -> zdt.minusMonths(12).toInstant()
                    PeriodDtoExtra.CUSTOM -> error("Handled above")
                }
            }

        val to: Instant =
            if (period == PeriodDtoExtra.CUSTOM) {
                checkNotNull(endDate).plusDays(1).atStartOfDay(zoneId).toInstant()
            } else {
                now
            }

        return Pair(from, to)
    }

    private fun resolveGranularity(
        period: PeriodDtoExtra,
        from: Instant,
        to: Instant,
    ): TimeGranularity =
        when (period) {
            PeriodDtoExtra.LAST_24_HOURS -> {
                TimeGranularity.HOUR
            }

            PeriodDtoExtra.CUSTOM -> {
                if (Duration
                        .between(from, to)
                        .toHours() <= 48
                ) {
                    TimeGranularity.HOUR
                } else {
                    TimeGranularity.DAY
                }
            }

            else -> {
                TimeGranularity.DAY
            }
        }
}
