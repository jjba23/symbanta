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

package org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound

import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import java.time.Instant

/**
 * Driven port: the per-application registry of event names (#4).
 */
interface EventTypeRepositoryPort {
    /** Idempotent registration: returns the existing definition when the name is already known. */
    fun registerIfAbsent(
        propertyId: PropertyId,
        name: String,
        description: String?,
        now: Instant,
    ): EventType

    /**
     * Idempotent bulk registration used by ingestion, which discovers names in batches. Names that
     * already exist keep their original definition.
     */
    fun registerAllIfAbsent(
        propertyId: PropertyId,
        names: Set<String>,
        now: Instant,
    ): List<EventType>

    fun listForDomain(domain: String): List<EventType>
}
