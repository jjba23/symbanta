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

package org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

// Spring proxies @Transactional adapters with CGLIB, so these classes must be open.
open class PostgresPropertiesRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : PropertyRepositoryPort {
    private val logger = KotlinLogging.logger {}

    private val propertyRowMapper =
        RowMapper { rs: ResultSet, _ ->
            val uuid =
                checkNotNull(rs.getObject("id", UUID::class.java)) {
                    "Property ID cannot be null in database"
                }

            Property(
                id = PropertyId(uuid),
                domain = rs.getString("domain") ?: "",
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at")?.toInstant(),
            )
        }

    @Transactional(readOnly = true)
    override fun byDomain(domain: String): Property? {
        logger.debug { "Fetching property by domain: $domain" }

        val params = MapSqlParameterSource("domain", domain)
        return namedParameterJdbcTemplate
            .query(findByDomainSql, params, propertyRowMapper)
            .firstOrNull()
    }

    @Transactional(readOnly = true)
    override fun byDomains(domains: Set<String>): Map<String, Property> {
        if (domains.isEmpty()) return emptyMap()

        logger.debug { "Fetching ${domains.size} properties by domain" }

        return namedParameterJdbcTemplate
            .query(findByDomainsSql, MapSqlParameterSource("domains", domains), propertyRowMapper)
            .associateBy { it.domain }
    }

    @Transactional
    override fun save(property: Property) {
        logger.info { "Saving property with ID: ${property.id.value}" }

        val params =
            MapSqlParameterSource()
                .addValue("id", property.id.value)
                .addValue("domain", property.domain)
                .addValue("created_at", Timestamp.from(property.createdAt))
                .addValue("updated_at", property.updatedAt?.let { Timestamp.from(it) })

        namedParameterJdbcTemplate.update(saveSql, params)
    }

    @Transactional(readOnly = true)
    override fun all(): List<Property> {
        logger.debug { "Fetching properties from database" }

        return namedParameterJdbcTemplate.query(
            getPropertiesSql,
            EmptySqlParameterSource.INSTANCE,
            propertyRowMapper,
        )
    }

    private companion object {
        val findByDomainSql =
            """
            SELECT id, domain, created_at, updated_at
            FROM properties
            WHERE domain = :domain
            """.trimIndent()

        val findByDomainsSql =
            """
            SELECT id, domain, created_at, updated_at
            FROM properties
            WHERE domain IN (:domains)
            """.trimIndent()

        val saveSql =
            """
            INSERT INTO properties (id, domain, created_at, updated_at)
            VALUES (:id, :domain, :created_at, :updated_at)
            ON CONFLICT (id) DO UPDATE
            SET domain = EXCLUDED.domain,
                updated_at = EXCLUDED.updated_at
            """.trimIndent()

        val getPropertiesSql =
            """
            SELECT id, domain, created_at, updated_at FROM properties ORDER BY domain
            """.trimIndent()
    }
}
