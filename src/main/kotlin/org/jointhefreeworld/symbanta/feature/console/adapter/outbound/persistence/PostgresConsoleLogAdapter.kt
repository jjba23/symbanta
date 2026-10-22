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

package org.jointhefreeworld.symbanta.feature.console.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel
import org.jointhefreeworld.symbanta.feature.console.port.outbound.ConsoleLogQueryPort
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

/**
 * Reads the `console` events captured by the telemetry client (#6).
 *
 * Spring proxies @Transactional adapters with CGLIB, so the class must be open.
 */
open class PostgresConsoleLogAdapter(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : ConsoleLogQueryPort {
    private val logger = KotlinLogging.logger {}

    private val consoleLogRowMapper =
        RowMapper { rs: ResultSet, _ ->
            ConsoleLogEntry(
                timestamp = rs.getTimestamp("created_at").toInstant(),
                level = ConsoleLogLevel.fromWire(rs.getString("level")) ?: ConsoleLogLevel.LOG,
                message = rs.getString("message") ?: "",
                url = rs.getString("url"),
                sessionId = rs.getString("session_id"),
                country = rs.getString("country"),
            )
        }

    @Transactional(readOnly = true)
    override fun recent(
        domain: String,
        level: ConsoleLogLevel?,
        limit: Int,
    ): List<ConsoleLogEntry> {
        logger.debug { "Fetching recent console logs from database" }

        val params =
            MapSqlParameterSource()
                .addValue("domain", domain)
                .addValue("level", level?.wireValue)
                .addValue("limit", limit)

        return namedParameterJdbcTemplate.query(getConsoleLogsSql, params, consoleLogRowMapper)
    }

    private companion object {
        val getConsoleLogsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            )
            SELECT
                e.created_at AS created_at,
                LOWER(COALESCE(e.props ->> 'level', 'log')) AS level,
                COALESCE(e.props ->> 'message', '') AS message,
                e.url AS url,
                e.session_id AS session_id,
                e.country AS country
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.event_name = 'console'
              AND (CAST(:level AS text) IS NULL OR LOWER(COALESCE(e.props ->> 'level', 'log')) = CAST(:level AS text))
            ORDER BY e.created_at DESC
            LIMIT :limit;
            """.trimIndent()
    }
}
