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

package org.jointhefreeworld.symbanta.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.jointhefreeworld.symbanta.application.port.inbound.IngestEventUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventDispatchPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.application.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.application.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.domain.model.AnalyticsEvent
import org.jointhefreeworld.symbanta.domain.model.EventId
import org.jointhefreeworld.symbanta.domain.model.EventNames
import org.jointhefreeworld.symbanta.domain.model.EventValidationException
import org.jointhefreeworld.symbanta.domain.model.PropertyNotFoundException
import org.jointhefreeworld.symbanta.domain.model.SessionId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Records a telemetry event for a registered application.
 *
 * Validation of the (cheap) payload happens on the caller's thread so clients get a prompt
 * answer; property lookup, event-type registration and the database write are handed to the
 * [EventDispatchPort] so HTTP requests are never blocked on I/O.
 */
class EventIngestionService(
    private val properties: PropertyRepositoryPort,
    private val eventTypes: EventTypeRepositoryPort,
    private val eventStore: EventStorePort,
    private val dispatcher: EventDispatchPort,
    private val clock: ClockPort,
) : IngestEventUseCase {
    private val logger = KotlinLogging.logger {}

    // Event type registration is additive, so a process-local set avoids one upsert per event.
    private val knownEventTypes = ConcurrentHashMap.newKeySet<String>()

    override fun register(command: RegisterEventCommand): EventId {
        val domain = command.domain.trim().lowercase()
        val name = command.name.trim()

        if (domain.isEmpty()) {
            throw EventValidationException("A property domain is required")
        }
        if (!EventNames.isValid(name)) {
            throw EventValidationException(
                "Invalid event name '$name': use 1-${EventNames.MAX_LENGTH} letters, digits or -_.:",
            )
        }

        val eventId = EventId(UUID.randomUUID())
        val receivedAt = clock.now()

        dispatcher.dispatch {
            persist(eventId, receivedAt, domain, name, command)
        }

        return eventId
    }

    private fun persist(
        eventId: EventId,
        receivedAt: java.time.Instant,
        domain: String,
        name: String,
        command: RegisterEventCommand,
    ) {
        withLoggingContext("eventId" to eventId.value.toString(), "domain" to domain) {
            val property =
                properties.byDomain(domain)
                    ?: throw PropertyNotFoundException(domain)

            val cacheKey = "${property.id.value}|$name"
            if (!knownEventTypes.contains(cacheKey)) {
                eventTypes.registerIfAbsent(property.id, name, null, receivedAt)
                knownEventTypes.add(cacheKey)
            }

            eventStore.save(
                AnalyticsEvent(
                    id = eventId,
                    propertyId = property.id,
                    sessionId = command.sessionId?.takeIf { it.isNotBlank() }?.let(::SessionId),
                    name = name,
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
                ),
            )

            logger.debug { "Stored '$name' event for ${property.domain}" }
        }
    }
}
