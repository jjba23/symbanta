/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
