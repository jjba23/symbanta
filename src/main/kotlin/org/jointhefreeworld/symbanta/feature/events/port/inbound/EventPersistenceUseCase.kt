/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
