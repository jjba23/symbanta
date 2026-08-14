package org.jointhefreeworld.symbanta.controllers

import org.jointhefreeworld.symbanta.openapi.controllers.*
import org.jointhefreeworld.symbanta.openapi.models.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class V1StatsSummaryControllerImpl : V1StatsSummaryController {
    override fun getStatsSummary(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1StatsSummaryResponseDto> =
        ResponseEntity.ok(
            V1StatsSummaryResponseDto(
                pageViews = 1,
                visitors = 0,
                bounceRate = 0F,
                averageDurationMillis = 100F,
            ),
        )
}
