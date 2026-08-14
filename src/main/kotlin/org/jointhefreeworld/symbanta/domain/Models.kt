package com.symbanta.analytics.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@JvmInline
value class PropertyId(
    val value: UUID,
)

@JvmInline
value class EventId(
    val value: UUID,
)

data class AnalyticsEvent(
    val id: EventId = EventId(UUID.randomUUID()),
    val propertyId: PropertyId,
    val eventName: String,
    val url: String? = null,
    val referrer: String? = null,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null,
    val language: String? = null,
    val propsJson: String = "{}",
    val createdAt: Instant = Instant.now(),
)

data class StatsSummary(
    val pageViews: Long,
    val visitors: Long,
    val bounceRate: BigDecimal,
    val averageDurationMillis: Double,
)

interface AnalyticsRepository {
    fun saveEvent(event: AnalyticsEvent): EventId

    fun getStatsSummary(
        domain: String,
        from: Instant,
        to: Instant,
    ): StatsSummary
}
