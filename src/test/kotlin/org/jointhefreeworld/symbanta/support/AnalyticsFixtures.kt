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

package org.jointhefreeworld.symbanta.support

import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.shared.domain.EventId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.jointhefreeworld.symbanta.shared.domain.SessionId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Inputs shared by the analytics port contracts and their PostgreSQL implementations.
 *
 * The timestamp is fixed so contract assertions describe behaviour rather than the clock.
 */
object AnalyticsFixtures {
    val BASE_TIME: Instant = Instant.parse("2026-01-10T12:00:00Z").truncatedTo(ChronoUnit.MICROS)

    fun property(
        domain: String,
        id: UUID = UUID.randomUUID(),
        createdAt: Instant = BASE_TIME.minusSeconds(86_400),
    ): Property = Property(PropertyId(id), domain, createdAt, null)

    @Suppress("LongParameterList")
    fun event(
        property: Property,
        name: String = EventNames.PAGE_VIEW,
        sessionId: String? = "session-1",
        url: String? = "https://${property.domain}/",
        referrer: String? = null,
        screenWidth: Int? = null,
        screenHeight: Int? = null,
        language: String? = null,
        userAgent: String? = null,
        props: Map<String, Any?> = emptyMap(),
        country: String? = null,
        headers: Map<String, String> = emptyMap(),
        createdAt: Instant = BASE_TIME,
    ): AnalyticsEvent =
        AnalyticsEvent(
            id = EventId(UUID.randomUUID()),
            propertyId = property.id,
            sessionId = sessionId?.let(::SessionId),
            name = name,
            url = url,
            referrer = referrer,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            language = language,
            userAgent = userAgent,
            props = props,
            country = country,
            headers = headers,
            createdAt = createdAt,
        )

    fun range(
        from: Instant = BASE_TIME.minusSeconds(3_600),
        to: Instant = BASE_TIME.plusSeconds(3_600),
    ): DateRange = DateRange(from, to)
}
