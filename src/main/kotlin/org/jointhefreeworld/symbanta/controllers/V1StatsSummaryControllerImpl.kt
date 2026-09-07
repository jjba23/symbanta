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
import java.time.temporal.ChronoUnit

const val TIMEZONE = "Europe/Amsterdam"

fun instantAtStartOfDay(i: Instant): Instant =
    LocalDate
        .ofInstant(i, ZoneId.of(TIMEZONE))
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
        .toInstant()

fun instantAtEndOfDay(i: Instant): Instant =
    LocalDate
        .ofInstant(i, ZoneId.of(TIMEZONE))
        .atStartOfDay()
        .atZone(ZoneId.of(TIMEZONE))
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
        val from: Instant =
            if (period == PeriodDto.CUSTOM) {
                checkNotNull(startDate).atStartOfDay().atZone(ZoneId.of(TIMEZONE)).toInstant()
            } else {
                when (period) {
                    PeriodDto.LAST_12_MONTHS -> Instant.now().minus(12, ChronoUnit.MONTHS)
                    PeriodDto.LAST_6_MONTHS -> Instant.now().minus(6, ChronoUnit.MONTHS)
                    PeriodDto.LAST_30_DAYS -> Instant.now().minus(30, ChronoUnit.DAYS)
                    PeriodDto.LAST_7_DAYS -> Instant.now().minus(7, ChronoUnit.DAYS)
                    PeriodDto.LAST_24_HOURS -> Instant.now().minus(24, ChronoUnit.HOURS)
                }
            }

        val to: Instant =
            if (period == PeriodDto.CUSTOM) {
                checkNotNull(endDate)
                    .plusDays(1)
                    .atStartOfDay()
                    .atZone(ZoneId.of(TIMEZONE))
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
