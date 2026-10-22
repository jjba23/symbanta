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

package org.jointhefreeworld.symbanta.support

import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort

/**
 * Supplies a [EventStorePort] adapter to [org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePortContract].
 *
 * Implementations live next to the adapter they exercise, which keeps the contract free of any
 * storage, container or migration detail.
 */
interface EventStoreFixture {
    fun createStore(): EventStorePort

    /** Persists (or registers) a property the events can reference, and returns it. */
    fun createProperty(domain: String): Property

    /** Removes every event and property so each test starts from a known state. */
    fun reset()

    /** Number of events currently stored, however the adapter counts them. */
    fun eventCount(): Long
}

/**
 * Supplies an [AnalyticsQueryPort] adapter to
 * [org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPortContract].
 */
interface AnalyticsQueryFixture {
    fun createQueries(): AnalyticsQueryPort

    /** Persists (or registers) a property whose events can be queried, and returns it. */
    fun createProperty(domain: String): Property

    /** Stores events for the current property. */
    fun write(events: List<AnalyticsEvent>)

    /** Removes every event and property so each test starts from a known state. */
    fun reset()
}
