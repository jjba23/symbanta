/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.EventTypeId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Event-type registry implemented with Spring Data JPA.
 *
 * Registration is idempotent: an existing `(property, name)` row is returned unchanged, and a
 * concurrent insert that wins the unique constraint race is resolved by re-reading it.
 *
 * Deliberately not transactional at this level: each lookup and insert runs in its own short
 * transaction, so a lost race aborts only the insert and the subsequent re-read can still run.
 */
open class JpaEventTypeRepository(
    private val repository: EventTypeJpaRepository,
) : EventTypeRepositoryPort {
    private val logger = KotlinLogging.logger {}

    override fun registerIfAbsent(
        propertyId: PropertyId,
        name: String,
        description: String?,
        now: Instant,
    ): EventType {
        repository.findByPropertyIdAndName(propertyId.value, name)?.let { return it.toDomain() }

        val entity =
            EventTypeEntity(
                id = UUID.randomUUID(),
                propertyId = propertyId.value,
                name = name,
                description = description,
                createdAt = now,
            )

        return try {
            repository.saveAndFlush(entity).toDomain()
        } catch (conflict: DataIntegrityViolationException) {
            logger.debug { "Event type '$name' was registered concurrently; reusing the existing row" }
            repository.findByPropertyIdAndName(propertyId.value, name)?.toDomain() ?: throw conflict
        }
    }

    override fun registerAllIfAbsent(
        propertyId: PropertyId,
        names: Set<String>,
        now: Instant,
    ): List<EventType> =
        names
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { registerIfAbsent(propertyId, it, null, now) }
            .toList()

    @Transactional(readOnly = true)
    override fun listForDomain(domain: String): List<EventType> = repository.findAllForDomain(domain).map { it.toDomain() }
}

internal fun EventTypeEntity.toDomain(): EventType =
    EventType(
        id = EventTypeId(id),
        propertyId = PropertyId(propertyId),
        name = name,
        description = description,
        createdAt = createdAt,
    )
