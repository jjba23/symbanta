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

import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent

/**
 * Driven port: the durable event store.
 *
 * [saveAll] is the only write path used by ingestion: batches are written in a single round trip
 * so that throughput is bound by the number of batches rather than the number of events.
 */
interface EventStorePort {
    /**
     * Persists every event and returns the number of rows written. Implementations must write the
     * batch atomically: either all rows land or none does, and an exception signals the failure.
     */
    fun saveAll(events: List<AnalyticsEvent>): Int
}
