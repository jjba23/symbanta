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

package org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Row of the `event_types` table: one registered event name per property.
 */
@Entity
@Table(name = "event_types")
class EventTypeEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "property_id", nullable = false)
    var propertyId: UUID = UUID.randomUUID(),
    @Column(name = "name", nullable = false)
    var name: String = "",
    @Column(name = "description")
    var description: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
)
