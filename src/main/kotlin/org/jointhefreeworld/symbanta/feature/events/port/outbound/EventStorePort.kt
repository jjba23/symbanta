/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
