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

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetStatsSummaryUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsSummaryController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1StatsSummaryResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsSummaryController(
    private val summary: GetStatsSummaryUseCase,
) : V1StatsSummaryController {
    override fun getStatsSummary(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1StatsSummaryResponseDto> {
        val result = summary.summary(statsQuery(domain, period, startDate, endDate))

        return ResponseEntity.ok(
            V1StatsSummaryResponseDto(
                from = result.from,
                to = result.to,
                pageViews = result.summary.pageViews.toInt(),
                visitors = result.summary.visitors.toInt(),
                bounceRate = result.summary.bounceRate,
                averageDurationMillis = result.summary.averageDurationMillis,
            ),
        )
    }
}
