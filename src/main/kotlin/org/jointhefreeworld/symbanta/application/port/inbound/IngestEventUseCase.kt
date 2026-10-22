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

package org.jointhefreeworld.symbanta.application.port.inbound

import org.jointhefreeworld.symbanta.domain.model.EventId

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

interface IngestEventUseCase {
    fun register(command: RegisterEventCommand): EventId
}
