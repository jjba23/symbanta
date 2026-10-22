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

package org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * Row of the `events` table.
 *
 * [props] and [headers] are PostgreSQL `jsonb` columns mapped as maps.
 *
 * Events are append-only, so the entity is [Persistable] and always reports itself as new:
 * `saveAll` then emits plain INSERTs (batched by Hibernate) instead of one merge per row, which
 * would issue a SELECT before every write and defeat the ingestion batching.
 */
@Entity
@Table(name = "events")
class EventEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var eventId: UUID = UUID.randomUUID(),
    @Column(name = "property_id", nullable = false)
    var propertyId: UUID = UUID.randomUUID(),
    @Column(name = "session_id")
    var sessionId: String? = null,
    @Column(name = "event_name", nullable = false)
    var name: String = "",
    @Column(name = "url")
    var url: String? = null,
    @Column(name = "referrer")
    var referrer: String? = null,
    @Column(name = "screen_width")
    var screenWidth: Int? = null,
    @Column(name = "screen_height")
    var screenHeight: Int? = null,
    @Column(name = "language")
    var language: String? = null,
    @Column(name = "user_agent")
    var userAgent: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "props", columnDefinition = "jsonb")
    var props: Map<String, Any?> = emptyMap(),
    @Column(name = "country")
    var country: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    var headers: Map<String, String> = emptyMap(),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
) : Persistable<UUID> {
    @field:Transient
    private var newEntity: Boolean = true

    override fun getId(): UUID = eventId

    override fun isNew(): Boolean = newEntity

    @PostLoad
    @PostPersist
    fun markPersisted() {
        newEntity = false
    }
}
