package org.jointhefreeworld.symbanta.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.*

@JvmInline
value class PropertyId(
    val value: UUID,
)

@JvmInline
value class SessionId(
    val value: String,
)

@JvmInline
value class EventId(
    val value: UUID,
)

data class AnalyticsEvent(
    val id: EventId,
    val propertyId: PropertyId,
    val sessionId: SessionId,
    val eventName: String,
    val url: String?,
    val referrer: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val language: String?,
    val userAgent: String?,
    val propsJson: String,
    val createdAt: Instant,
)

data class StatsSummary(
    val pageViews: Long,
    val visitors: Long,
    val bounceRate: BigDecimal,
    val averageDurationMillis: Double,
)
