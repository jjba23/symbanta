/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.domain

import org.jointhefreeworld.symbanta.shared.domain.EventTypeId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import java.time.Instant

/**
 * An event name registered for a single application (property).
 *
 * Symbanta does not require a schema up-front: any event name sent by an instrumented
 * application is registered automatically the first time it is ingested, and can also be
 * declared explicitly through the API so dashboards can discover it before it happens.
 */
data class EventType(
    val id: EventTypeId,
    val propertyId: PropertyId,
    val name: String,
    val description: String?,
    val createdAt: Instant,
)
