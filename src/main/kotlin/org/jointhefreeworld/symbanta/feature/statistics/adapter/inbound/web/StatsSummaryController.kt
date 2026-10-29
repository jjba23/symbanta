/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
