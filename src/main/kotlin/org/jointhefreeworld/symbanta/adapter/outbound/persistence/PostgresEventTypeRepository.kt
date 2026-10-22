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

package org.jointhefreeworld.symbanta.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.application.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.domain.model.EventType
import org.jointhefreeworld.symbanta.domain.model.EventTypeId
import org.jointhefreeworld.symbanta.domain.model.PropertyId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

// Spring proxies @Transactional adapters with CGLIB, so these classes must be open.
open class PostgresEventTypeRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : EventTypeRepositoryPort {
    private val logger = KotlinLogging.logger {}

    private val eventTypeRowMapper =
        RowMapper { rs: ResultSet, _ ->
            EventType(
                id = EventTypeId(rs.getObject("id", UUID::class.java)),
                propertyId = PropertyId(rs.getObject("property_id", UUID::class.java)),
                name = rs.getString("name"),
                description = rs.getString("description"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }

    @Transactional
    override fun registerIfAbsent(
        propertyId: PropertyId,
        name: String,
        description: String?,
        now: Instant,
    ): EventType {
        val params =
            MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("propertyId", propertyId.value)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("createdAt", Timestamp.from(now))

        return namedParameterJdbcTemplate
            .query(registerSql, params, eventTypeRowMapper)
            .single()
    }

    @Transactional(readOnly = true)
    override fun listForDomain(domain: String): List<EventType> =
        namedParameterJdbcTemplate.query(listSql, MapSqlParameterSource("domain", domain), eventTypeRowMapper)

    private companion object {
        // DO UPDATE (rather than DO NOTHING) guarantees RETURNING also yields the existing row.
        val registerSql =
            """
            INSERT INTO event_types (id, property_id, name, description, created_at)
            VALUES (:id, :propertyId, :name, :description, :createdAt)
            ON CONFLICT (property_id, name) DO UPDATE
            SET name = EXCLUDED.name
            RETURNING id, property_id, name, description, created_at
            """.trimIndent()

        val listSql =
            """
            SELECT et.id, et.property_id, et.name, et.description, et.created_at
            FROM event_types et
            JOIN properties p ON p.id = et.property_id
            WHERE p.domain = :domain
            ORDER BY et.name
            """.trimIndent()
    }
}
