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
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsSummaryController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1StatsSummaryResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val TIMEZONE = "Europe/Amsterdam"

val ZONE_ID: ZoneId = ZoneId.of(TIMEZONE)

fun instantAtStartOfDay(i: Instant): Instant =
    LocalDate
        .ofInstant(i, ZONE_ID)
        .atStartOfDay(ZONE_ID)
        .toInstant()

fun instantAtEndOfDay(i: Instant): Instant =
    LocalDate
        .ofInstant(i, ZONE_ID)
        .plusDays(1)
        .atStartOfDay(ZONE_ID)
        .toInstant()

@RestController
class V1StatsSummaryControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : V1StatsSummaryController {
    override fun getStatsSummary(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1StatsSummaryResponseDto> {
        val now = clock.instant()
        val zdt = now.atZone(ZONE_ID)

        val from: Instant =
            if (period == PeriodDto.CUSTOM) {
                checkNotNull(startDate).atStartOfDay(ZONE_ID).toInstant()
            } else {
                when (period) {
                    PeriodDto.LAST_12_MONTHS -> zdt.minusMonths(12).toInstant()
                    PeriodDto.LAST_6_MONTHS -> zdt.minusMonths(6).toInstant()
                    PeriodDto.LAST_30_DAYS -> zdt.minusDays(30).toInstant()
                    PeriodDto.LAST_7_DAYS -> zdt.minusDays(7).toInstant()
                    PeriodDto.LAST_24_HOURS -> zdt.minusHours(24).toInstant()
                    PeriodDto.CUSTOM -> throw IllegalArgumentException("Custom period requires dates")
                }
            }

        val to: Instant =
            if (period == PeriodDto.CUSTOM) {
                checkNotNull(endDate)
                    .plusDays(1)
                    .atStartOfDay(ZONE_ID)
                    .toInstant()
            } else {
                now
            }

        val summary = analyticsRepository.getStatsSummary(domain = domain, from = from, to = to)
        return ResponseEntity.ok(
            V1StatsSummaryResponseDto(
                pageViews = summary.pageViews.toInt(),
                visitors = summary.visitors.toInt(),
                bounceRate = summary.bounceRate,
                averageDurationMillis = summary.averageDurationMillis,
                from = from,
                to = to,
            ),
        )
    }
}
