/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
