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

package org.jointhefreeworld.symbanta.feature.events.service

import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.events.port.inbound.IngestEventUseCase
import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventQueuePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import org.jointhefreeworld.symbanta.shared.domain.EventId
import org.jointhefreeworld.symbanta.shared.domain.EventValidationException
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import java.util.UUID

/**
 * Records a telemetry event for a registered application.
 *
 * Only the cheap payload validation runs on the caller's thread: property lookup, event-type
 * registration and the database write happen in the queue workers (see
 * [org.jointhefreeworld.symbanta.feature.events.adapter.outbound.queue.BatchingEventDispatcher]),
 * so HTTP requests never block on I/O.
 */
class EventIngestionService(
    private val queue: EventQueuePort,
    private val clock: ClockPort,
) : IngestEventUseCase {
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

        queue.enqueue(
            PendingEvent(
                eventId = eventId,
                receivedAt = clock.now(),
                command = command.copy(domain = domain, name = name),
            ),
        )

        return eventId
    }
}
