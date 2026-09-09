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

package org.jointhefreeworld.symbanta.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.domain.PropertiesRepository
import org.jointhefreeworld.symbanta.domain.Property
import org.jointhefreeworld.symbanta.domain.PropertyId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.util.*

@Repository
class PostgresPropertiesRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : PropertiesRepository {
    private val logger = KotlinLogging.logger {}

    private val propertyRowMapper =
        RowMapper { rs: ResultSet, _ ->
            // Safely extract PostgreSQL UUID column as java.util.UUID object
            val uuid =
                checkNotNull(rs.getObject("id", UUID::class.java)) {
                    "Property ID cannot be null in database"
                }

            // Safely extract timestamps handling NULL values or TIMESTAMPTZ columns
            val createdAtTimestamp = rs.getTimestamp("created_at").toInstant()
            val updatedAtTimestamp = rs.getTimestamp("updated_at")?.toInstant()

            Property(
                id = PropertyId(uuid),
                domain = rs.getString("domain") ?: "",
                createdAt = createdAtTimestamp,
                updatedAt = updatedAtTimestamp,
            )
        }

    @Transactional(readOnly = true)
    override fun getProperties(): List<Property> {
        logger.info { "Fetching properties from database" }

        val properties =
            namedParameterJdbcTemplate
                .query(getPropertiesSql, EmptySqlParameterSource.INSTANCE, propertyRowMapper)
        logger.info { "Properties: $properties" }
        return properties
    }

    private companion object {
        val getPropertiesSql =
            """
            SELECT id, domain, created_at, updated_at FROM properties
            """.trimIndent()
    }
}
