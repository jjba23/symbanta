package org.jointhefreeworld.symbanta.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.jointhefreeworld.symbanta.domain.*
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class PostgresAnalyticsRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : AnalyticsRepository {
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

    private val propertyRowMapper =
        RowMapper { rs: ResultSet, _ ->
            Property(
                id = PropertyId(rs.getObject("id", UUID::class.java)),
                domain = rs.getString("domain"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
                updatedAt = rs.getTimestamp("updated_at")?.toInstant(),
            )
        }

    @Transactional
    override fun saveEvent(event: AnalyticsEvent): EventId =
        withLoggingContext(
            "eventId" to event.id.value.toString(),
            "propertyId" to event.propertyId.value.toString(),
            "sessionId" to event.sessionId?.value,
            "eventName" to event.eventName,
            "createdAt" to event.createdAt.toString(),
        ) {
            logger.info { "Saving analytics event to database" }

            val params =
                MapSqlParameterSource()
                    .addValue("id", event.id.value)
                    .addValue("propertyId", event.propertyId.value)
                    .addValue("sessionId", event.sessionId?.value)
                    .addValue("eventName", event.eventName)
                    .addValue("url", event.url)
                    .addValue("referrer", event.referrer)
                    .addValue("screenWidth", event.screenWidth)
                    .addValue("screenHeight", event.screenHeight)
                    .addValue("language", event.language)
                    .addValue("userAgent", event.userAgent)
                    .addValue("props", event.propsJson)
                    .addValue("createdAt", Timestamp.from(event.createdAt))

            namedParameterJdbcTemplate.update(saveEventSql, params)

            logger.info { "Successfully saved analytics event to database" }
            event.id
        }

    @Transactional(readOnly = true)
    override fun getStatsSummary(
        domain: String,
        from: Instant,
        to: Instant,
    ): StatsSummary =
        withLoggingContext(
            "domain" to domain,
            "from" to from.toString(),
            "to" to to.toString(),
        ) {
            logger.info { "Fetching analytics stats summary from database" }

            val params =
                MapSqlParameterSource()
                    .addValue("domain", domain)
                    .addValue("from", Timestamp.from(from))
                    .addValue("to", Timestamp.from(to))

            val summary =
                namedParameterJdbcTemplate
                    .query(getStatsSummarySql, params, statsSummaryRowMapper)
                    .single()

            withLoggingContext(
                "pageViews" to summary.pageViews.toString(),
                "visitors" to summary.visitors.toString(),
                "bounceRate" to summary.bounceRate.toString(),
                "averageDurationMillis" to summary.averageDurationMillis.toString(),
            ) {
                logger.info { "Successfully calculated analytics stats summary" }
            }

            summary
        }

    @Transactional(readOnly = true)
    override fun getPropertyByDomain(domain: String): Property? =
        withLoggingContext(
            "domain" to domain,
        ) {
            logger.info { "Fetching property by domain from database" }

            val params = MapSqlParameterSource().addValue("domain", domain)

            val property =
                namedParameterJdbcTemplate
                    .query(getPropertyByDomainSql, params, propertyRowMapper)
                    .singleOrNull()

            if (property != null) {
                withLoggingContext("propertyId" to property.id.value.toString()) {
                    logger.info { "Successfully fetched property by domain" }
                }
            } else {
                logger.info { "No property found for domain" }
            }

            property
        }

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
                :createdAt
            )
            """.trimIndent()

        val getPropertyByDomainSql =
            """
            SELECT id, domain, created_at, updated_at FROM properties 
            WHERE domain = :domain
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
    }
}
