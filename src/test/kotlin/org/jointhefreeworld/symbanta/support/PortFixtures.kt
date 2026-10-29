/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
