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

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.EventStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.RouteStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.StatsSummary
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeSeriesPoint
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentStat
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * PostgreSQL implementation of the analytics read port.
 *
 * Every query resolves the property by domain and aggregates `page-view` events inside the
 * requested window; nothing here is aware of HTTP or of the OpenAPI contract.
 *
 * Spring proxies @Transactional adapters with CGLIB, so the class must be open.
 */
open class PostgresAnalyticsQueryAdapter(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : AnalyticsQueryPort {
    private val logger = KotlinLogging.logger {}

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
    }
}
