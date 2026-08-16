package org.jointhefreeworld.symbanta.controllers

import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsSummaryController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1StatsSummaryResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@RestController
class V1StatsSummaryControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
) : V1StatsSummaryController {
    override fun getStatsSummary(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1StatsSummaryResponseDto> {
        val from: Instant =
            if (period == PeriodDto.CUSTOM) {
                checkNotNull(startDate).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
            } else {
                // TODO period support
                Instant.now().minus(1, ChronoUnit.DAYS)
            }

        val to: Instant =
            if (period == PeriodDto.CUSTOM) {
                checkNotNull(endDate).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()
            } else {
                // TODO period support
                Instant.now()
            }

        val summary = analyticsRepository.getStatsSummary(domain = domain, from = from, to = to)
        return ResponseEntity.ok(
            V1StatsSummaryResponseDto(
                pageViews = summary.pageViews.toInt(),
                visitors = summary.visitors.toInt(),
                bounceRate = summary.bounceRate,
                averageDurationMillis = summary.averageDurationMillis,
            ),
        )
    }
}
