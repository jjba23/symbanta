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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

/**
 * Writes events to PostgreSQL with JDBC batching.
 *
 * A batch is sent as a single statement with many parameter sets, so the cost of a burst of
 * events is dominated by the number of round trips (batches) rather than by the number of events.
 * The whole batch runs in one transaction: a failed flush leaves no partial batch behind.
 *
 * Spring proxies @Transactional adapters with CGLIB, so the class must be open.
 */
open class PostgresEventStore(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : EventStorePort {
    private val logger = KotlinLogging.logger {}
    private val objectMapper = jacksonObjectMapper()

    @Transactional
    override fun saveAll(events: List<AnalyticsEvent>): Int {
        if (events.isEmpty()) return 0

        val parameters = events.map { toParameters(it) }.toTypedArray()
        val written = namedParameterJdbcTemplate.batchUpdate(saveEventSql, parameters).sum()

        logger.debug { "Wrote $written of ${events.size} event(s) in one batch" }
        return written
    }

    private fun toParameters(event: AnalyticsEvent): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("id", event.id.value)
            .addValue("propertyId", event.propertyId.value)
            .addValue("sessionId", event.sessionId?.value)
            .addValue("eventName", event.name)
            .addValue("url", event.url)
            .addValue("referrer", event.referrer)
            .addValue("screenWidth", event.screenWidth)
            .addValue("screenHeight", event.screenHeight)
            .addValue("language", event.language)
            .addValue("userAgent", event.userAgent)
            .addValue("props", objectMapper.writeValueAsString(event.props))
            .addValue("country", event.country)
            .addValue("headers", objectMapper.writeValueAsString(event.headers))
            .addValue("createdAt", Timestamp.from(event.createdAt))

    private companion object {
        val saveEventSql =
            """
            INSERT INTO events (
                id,
                property_id,
                session_id,
                event_name,
                url,
                referrer,
                screen_width,
                screen_height,
                language,
                user_agent,
                props,
                country,
                headers,
                created_at
            ) VALUES (
                :id,
                :propertyId,
                :sessionId,
                :eventName,
                :url,
                :referrer,
                :screenWidth,
                :screenHeight,
                :language,
                :userAgent,
                :props::jsonb,
                :country,
                :headers::jsonb,
                :createdAt
            )
            """.trimIndent()
    }
}
