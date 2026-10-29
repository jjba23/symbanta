/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
