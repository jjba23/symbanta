/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.port.inbound

import org.jointhefreeworld.symbanta.shared.domain.EventId

/**
 * What an inbound adapter (HTTP, a queue consumer, a CLI) must provide to record telemetry.
 *
 * Transport specific concerns — request headers, client country, DTO validation — are already
 * resolved by the adapter: the application layer only sees this command.
 */
data class RegisterEventCommand(
    val domain: String,
    val name: String,
    val sessionId: String?,
    val url: String?,
    val referrer: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val language: String?,
    val userAgent: String?,
    val props: Map<String, Any?>,
    val country: String?,
    val headers: Map<String, String>,
)

/**
 * Validates an event and hands it to the ingestion queue. Returns as soon as the event is accepted.
 */
interface IngestEventUseCase {
    fun register(command: RegisterEventCommand): EventId
}
