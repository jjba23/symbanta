/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.port.inbound.EventPersistenceUseCase
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.SessionId
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a batch of accepted events into rows.
 *
 * Property lookup and event-type registration are done once per batch (and, for event types,
 * once per process) so a batch of N events costs a constant number of small queries plus the
 * single batched insert.
 */
class EventPersistenceService(
    private val properties: PropertyRepositoryPort,
    private val eventTypes: EventTypeRepositoryPort,
    private val eventStore: EventStorePort,
) : EventPersistenceUseCase {
    private val logger = KotlinLogging.logger {}

    // Event type registration is additive, so a process-local set avoids one upsert per event.
    private val knownEventTypes = ConcurrentHashMap.newKeySet<String>()

    override fun persist(events: List<PendingEvent>): Int {
        if (events.isEmpty()) return 0

        val propertiesByDomain = properties.byDomains(events.map { it.command.domain }.toSet())

        val resolved = events.mapNotNull { pending -> propertiesByDomain[pending.command.domain]?.let { it to pending } }
        val unknown = events.size - resolved.size
        if (unknown > 0) {
            val missing = events.map { it.command.domain }.filter { it !in propertiesByDomain }.distinct()
            logger.warn { "Discarded $unknown event(s) of a batch of ${events.size} for unregistered domain(s): $missing" }
        }
        if (resolved.isEmpty()) return 0

        registerEventTypes(resolved)

        val rows = resolved.map { (property, pending) -> pending.toEvent(property) }
        return eventStore.saveAll(rows)
    }

    private fun registerEventTypes(resolved: List<Pair<Property, PendingEvent>>) {
        resolved
            .groupBy { (property, _) -> property.id }
            .forEach { (propertyId, group) ->
                val names = group.map { (_, pending) -> pending.command.name }.distinct()
                // Only names this process has not registered yet reach the database.
                val unseen = names.filterNot { name -> "${propertyId.value}|$name" in knownEventTypes }

                if (unseen.isNotEmpty()) {
                    eventTypes.registerAllIfAbsent(
                        propertyId = propertyId,
                        names = unseen.toSet(),
                        now = group.minOf { (_, pending) -> pending.receivedAt },
                    )
                    // Marked only after a successful upsert, so a failure is retried by the next batch.
                    unseen.forEach { name -> knownEventTypes.add("${propertyId.value}|$name") }
                }
            }
    }

    private fun PendingEvent.toEvent(property: Property): AnalyticsEvent =
        AnalyticsEvent(
            id = eventId,
            propertyId = property.id,
            sessionId = command.sessionId?.takeIf { it.isNotBlank() }?.let(::SessionId),
            name = command.name,
            url = command.url,
            referrer = command.referrer,
            screenWidth = command.screenWidth,
            screenHeight = command.screenHeight,
            language = command.language,
            userAgent = command.userAgent,
            props = command.props,
            country = command.country,
            headers = command.headers,
            createdAt = receivedAt,
        )
}
