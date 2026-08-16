package org.jointhefreeworld.symbanta.domain

import java.time.Instant

interface AnalyticsRepository {
    fun saveEvent(event: AnalyticsEvent): EventId

    fun getStatsSummary(
        domain: String,
        from: Instant,
        to: Instant,
    ): StatsSummary
}
