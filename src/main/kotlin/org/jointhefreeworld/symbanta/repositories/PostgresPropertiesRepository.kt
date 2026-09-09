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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
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
            Property(
                id = PropertyId(UUID.fromString(rs.getString("id"))),
                domain = rs.getString("domain"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at").toInstant(),
            )
        }

    @Transactional(readOnly = true)
    override fun getProperties(): List<Property> {
        logger.info { "Fetching properties from database" }

        val params =
            MapSqlParameterSource()

        val properties =
            namedParameterJdbcTemplate
                .query(getPropertiesSql, params, propertyRowMapper)

        return properties
    }

    private companion object {
        val getPropertiesSql =
            """
            SELECT id, domain, created_at, updated_at FROM properties
            """.trimIndent()
    }
}
