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

package org.jointhefreeworld.symbanta.domain.model

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
