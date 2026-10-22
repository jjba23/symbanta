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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.jointhefreeworld.symbanta.application.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.domain.model.AnalyticsEvent
import org.jointhefreeworld.symbanta.domain.model.ConsoleLogEntry
import org.jointhefreeworld.symbanta.domain.model.ConsoleLogLevel
import org.jointhefreeworld.symbanta.domain.model.CountryStat
import org.jointhefreeworld.symbanta.domain.model.DateRange
import org.jointhefreeworld.symbanta.domain.model.EventId
import org.jointhefreeworld.symbanta.domain.model.EventStat
import org.jointhefreeworld.symbanta.domain.model.RouteStat
import org.jointhefreeworld.symbanta.domain.model.ScreenSizeStat
import org.jointhefreeworld.symbanta.domain.model.StatsSummary
import org.jointhefreeworld.symbanta.domain.model.TimeGranularity
import org.jointhefreeworld.symbanta.domain.model.TimeSeriesPoint
import org.jointhefreeworld.symbanta.domain.model.UserAgentStat
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * PostgreSQL adapter implementing both sides of the event store: the write port used during
 * ingestion and the read port used by dashboards.
 */
// Spring proxies @Transactional adapters with CGLIB, so these classes must be open.
open class PostgresAnalyticsRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : EventStorePort,
    AnalyticsQueryPort {
    private val logger = KotlinLogging.logger {}
    private val objectMapper = jacksonObjectMapper()

    private val statsSummaryRowMapper =
        RowMapper { rs: ResultSet, _ ->
            StatsSummary(
                pageViews = rs.getLong("page_views"),
                visitors = rs.getLong("visitors"),
                bounceRate = rs.getBigDecimal("bounce_rate"),
                averageDurationMillis = rs.getBigDecimal("avg_duration_ms"),
            )
        }

    private val timeSeriesPointRowMapper =
        RowMapper { rs: ResultSet, _ ->
            TimeSeriesPoint(
                timestamp = rs.getTimestamp("bucket").toInstant(),
                pageViews = rs.getLong("page_views"),
                visitors = rs.getLong("visitors"),
            )
        }

    private val screenSizeStatRowMapper =
        RowMapper { rs: ResultSet, _ ->
            ScreenSizeStat(
                width = rs.getInt("width"),
                height = rs.getInt("height"),
                pageViews = rs.getLong("page_views"),
                visitors = rs.getLong("visitors"),
            )
        }

    private val userAgentStatRowMapper =
        RowMapper { rs: ResultSet, _ ->
            UserAgentStat(
                userAgent = rs.getString("user_agent") ?: "???",
                pageViews = rs.getLong("page_views"),
                visitors = rs.getLong("visitors"),
            )
        }

    private val routeStatRowMapper =
        RowMapper { rs: ResultSet, _ ->
            RouteStat(
                route = rs.getString("route") ?: "???",
                pageViews = rs.getLong("page_views"),
                visitors = rs.getLong("visitors"),
            )
        }

    private val eventStatRowMapper =
        RowMapper { rs: ResultSet, _ ->
            EventStat(
                eventName = rs.getString("event_name") ?: "???",
                occurrences = rs.getLong("occurrences"),
                visitors = rs.getLong("visitors"),
            )
        }

    private val countryStatRowMapper =
        RowMapper { rs: ResultSet, _ ->
            CountryStat(
                country = rs.getString("country") ?: UNKNOWN_COUNTRY,
                pageViews = rs.getLong("page_views"),
                visitors = rs.getLong("visitors"),
            )
        }

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

    @Transactional
    override fun save(event: AnalyticsEvent): EventId =
        withLoggingContext(
            "eventId" to event.id.value.toString(),
            "propertyId" to event.propertyId.value.toString(),
            "sessionId" to event.sessionId?.value,
            "eventName" to event.name,
            "createdAt" to event.createdAt.toString(),
        ) {
            logger.debug { "Saving analytics event to database" }

            val params =
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

            namedParameterJdbcTemplate.update(saveEventSql, params)
            event.id
        }

    @Transactional(readOnly = true)
    override fun summary(
        domain: String,
        range: DateRange,
    ): StatsSummary =
        withLoggingContext(
            "domain" to domain,
            "from" to range.from.toString(),
            "to" to range.to.toString(),
        ) {
            logger.debug { "Fetching analytics stats summary from database" }

            namedParameterJdbcTemplate
                .query(
                    getStatsSummarySql,
                    rangeParameters(domain, range),
                    statsSummaryRowMapper,
                ).single()
        }

    @Transactional(readOnly = true)
    override fun timeSeries(
        domain: String,
        range: DateRange,
        granularity: TimeGranularity,
    ): List<TimeSeriesPoint> {
        logger.debug { "Fetching time-series analytics stats from database" }

        val params =
            rangeParameters(domain, range)
                .addValue("granularity", granularity.unit)
                .addValue("intervalStep", granularity.intervalStep)

        return namedParameterJdbcTemplate.query(getTimeSeriesStatsSql, params, timeSeriesPointRowMapper)
    }

    @Transactional(readOnly = true)
    override fun screenSizes(
        domain: String,
        range: DateRange,
    ): List<ScreenSizeStat> {
        logger.debug { "Fetching screen sizes breakdown from database" }
        return namedParameterJdbcTemplate.query(getScreenSizeStatsSql, rangeParameters(domain, range), screenSizeStatRowMapper)
    }

    @Transactional(readOnly = true)
    override fun userAgents(
        domain: String,
        range: DateRange,
    ): List<UserAgentStat> {
        logger.debug { "Fetching user agents breakdown from database" }
        return namedParameterJdbcTemplate.query(getUserAgentStatsSql, rangeParameters(domain, range), userAgentStatRowMapper)
    }

    @Transactional(readOnly = true)
    override fun routes(
        domain: String,
        range: DateRange,
    ): List<RouteStat> {
        logger.debug { "Fetching routes breakdown from database" }

        val params = rangeParameters(domain, range).addValue("routeDomain", domain)

        return namedParameterJdbcTemplate.query(getRouteStatsSql, params, routeStatRowMapper)
    }

    @Transactional(readOnly = true)
    override fun events(
        domain: String,
        range: DateRange,
    ): List<EventStat> {
        logger.debug { "Fetching event type breakdown from database" }
        return namedParameterJdbcTemplate.query(getEventStatsSql, rangeParameters(domain, range), eventStatRowMapper)
    }

    @Transactional(readOnly = true)
    override fun countries(
        domain: String,
        range: DateRange,
    ): List<CountryStat> {
        logger.debug { "Fetching country breakdown from database" }
        return namedParameterJdbcTemplate.query(getCountryStatsSql, rangeParameters(domain, range), countryStatRowMapper)
    }

    @Transactional(readOnly = true)
    override fun consoleLogs(
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

    private fun rangeParameters(
        domain: String,
        range: DateRange,
    ): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("domain", domain)
            .addValue("from", Timestamp.from(range.from))
            .addValue("to", Timestamp.from(range.to))

    private companion object {
        const val UNKNOWN_COUNTRY = "ZZ"

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

        val getStatsSummarySql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            ),
            session_metrics AS (
                SELECT
                    e.session_id,
                    COUNT(*) FILTER (WHERE e.event_name = 'page-view') AS pv_count
                FROM events e
                JOIN target_property tp ON e.property_id = tp.id
                WHERE e.created_at >= :from AND e.created_at < :to AND e.session_id IS NOT NULL
                GROUP BY e.session_id
            ),
            event_deltas AS (
                SELECT
                    e.session_id,
                    EXTRACT(EPOCH FROM (e.created_at - LAG(e.created_at) OVER (PARTITION BY e.session_id ORDER BY e.created_at))) AS gap_sec
                FROM events e
                JOIN target_property tp ON e.property_id = tp.id
                WHERE e.created_at >= :from AND e.created_at <= :to AND e.session_id IS NOT NULL
            ),
            active_durations AS (
                SELECT
                    session_id,
                    SUM(CASE WHEN gap_sec <= 30 THEN gap_sec * 1000 ELSE 0 END) AS active_ms
                FROM event_deltas
                WHERE gap_sec IS NOT NULL
                GROUP BY session_id
            )
            SELECT
                COALESCE(SUM(sm.pv_count), 0)::bigint AS page_views,
                COALESCE(COUNT(sm.session_id), 0)::bigint AS visitors,
                COALESCE(
                    (COUNT(*) FILTER (WHERE sm.pv_count = 1 AND COALESCE(ad.active_ms, 0) < 10000)::numeric /
                     NULLIF(COUNT(sm.session_id), 0)) * 100,
                    0.00
                )::numeric AS bounce_rate,
                COALESCE(AVG(ad.active_ms), 0.0)::double precision AS avg_duration_ms
            FROM session_metrics sm
            LEFT JOIN active_durations ad ON sm.session_id = ad.session_id;
            """.trimIndent()

        val getTimeSeriesStatsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            ),
            time_buckets AS (
                SELECT generate_series(
                    date_trunc(:granularity, :from::timestamp),
                    date_trunc(:granularity, :to::timestamp),
                    :intervalStep::interval
                ) AS bucket
            ),
            aggregated_events AS (
                SELECT
                    date_trunc(:granularity, e.created_at) AS bucket,
                    COUNT(*) FILTER (WHERE e.event_name = 'page-view') AS page_views,
                    COUNT(DISTINCT e.session_id) AS visitors
                FROM events e
                JOIN target_property tp ON e.property_id = tp.id
                WHERE e.created_at >= :from AND e.created_at <= :to
                GROUP BY 1
            )
            SELECT
                tb.bucket,
                COALESCE(ae.page_views, 0)::bigint AS page_views,
                COALESCE(ae.visitors, 0)::bigint AS visitors
            FROM time_buckets tb
            LEFT JOIN aggregated_events ae ON tb.bucket = ae.bucket
            ORDER BY tb.bucket;
            """.trimIndent()

        val getScreenSizeStatsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            )
            SELECT
                e.screen_width AS width,
                e.screen_height AS height,
                COUNT(*) FILTER (WHERE e.event_name = 'page-view') AS page_views,
                COUNT(DISTINCT e.session_id) AS visitors
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.created_at >= :from AND e.created_at <= :to
              AND e.screen_width IS NOT NULL
              AND e.screen_height IS NOT NULL
            GROUP BY e.screen_width, e.screen_height
            ORDER BY page_views DESC;
            """.trimIndent()

        val getUserAgentStatsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            )
            SELECT
                COALESCE(e.user_agent, '???') AS user_agent,
                COUNT(*) FILTER (WHERE e.event_name = 'page-view') AS page_views,
                COUNT(DISTINCT e.session_id) AS visitors
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.created_at >= :from AND e.created_at <= :to
            GROUP BY e.user_agent
            ORDER BY page_views DESC;
            """.trimIndent()

        val getRouteStatsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            )
            SELECT
                REGEXP_REPLACE(e.url, '^https?://' || :routeDomain, '') AS route,
                COUNT(*) FILTER (WHERE e.event_name = 'page-view') AS page_views,
                COUNT(DISTINCT e.session_id) AS visitors
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.created_at >= :from AND e.created_at <= :to
            GROUP BY 1
            ORDER BY page_views DESC;
            """.trimIndent()

        val getEventStatsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            )
            SELECT
                e.event_name AS event_name,
                COUNT(*)::bigint AS occurrences,
                COUNT(DISTINCT e.session_id)::bigint AS visitors
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.created_at >= :from AND e.created_at <= :to
            GROUP BY e.event_name
            ORDER BY occurrences DESC, e.event_name ASC;
            """.trimIndent()

        val getCountryStatsSql =
            """
            WITH target_property AS (
                SELECT id FROM properties WHERE domain = :domain
            )
            SELECT
                COALESCE(e.country, '$UNKNOWN_COUNTRY') AS country,
                COUNT(*) FILTER (WHERE e.event_name = 'page-view') AS page_views,
                COUNT(DISTINCT e.session_id) AS visitors
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.created_at >= :from AND e.created_at <= :to
            GROUP BY 1
            ORDER BY page_views DESC;
            """.trimIndent()

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
