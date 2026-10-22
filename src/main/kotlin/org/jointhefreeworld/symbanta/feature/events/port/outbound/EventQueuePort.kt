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

package org.jointhefreeworld.symbanta.feature.events.port.outbound

import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.shared.domain.EventId
import java.time.Instant

/**
 * A validated event that has been accepted by application but not yet written.
 */
data class PendingEvent(
    val eventId: EventId,
    val receivedAt: Instant,
    val command: RegisterEventCommand,
)

/**
 * Driven port: hand an accepted event to the durable ingestion pipeline.
 *
 * The contract is deliberately narrow. [enqueue] must not block the request thread and must not
 * throw when the pipeline is saturated; over-capacity is reported through telemetry instead.
 */
interface EventQueuePort {
    fun enqueue(event: PendingEvent)
}
