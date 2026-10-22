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

package org.jointhefreeworld.symbanta.feature.events.port.inbound

import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent

/**
 * Use case invoked by the ingestion queue worker: resolve properties and event types, then write
 * the whole batch through the event store.
 */
interface EventPersistenceUseCase {
    /** Returns the number of rows actually written; events for unknown domains are discarded. */
    fun persist(events: List<PendingEvent>): Int
}
