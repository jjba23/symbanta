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

package org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.springframework.transaction.annotation.Transactional

/**
 * Event store implemented with Spring Data JPA.
 *
 * `saveAll` runs inside one transaction and Hibernate flushes it as multi-row INSERTs
 * (`hibernate.jdbc.batch_size`), so a worker batch still costs O(1) round trips. [EventEntity] is
 * [org.springframework.data.domain.Persistable], which keeps `saveAll` on the INSERT path instead
 * of a per-row merge.
 */
open class JpaEventStore(
    private val repository: EventJpaRepository,
) : EventStorePort {
    private val logger = KotlinLogging.logger {}

    @Transactional
    override fun saveAll(events: List<AnalyticsEvent>): Int {
        if (events.isEmpty()) return 0

        repository.saveAll(events.map { it.toEntity() })

        logger.debug { "Queued ${events.size} event(s) for a batched write" }
        return events.size
    }
}

internal fun AnalyticsEvent.toEntity(): EventEntity =
    EventEntity(
        eventId = id.value,
        propertyId = propertyId.value,
        sessionId = sessionId?.value,
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
