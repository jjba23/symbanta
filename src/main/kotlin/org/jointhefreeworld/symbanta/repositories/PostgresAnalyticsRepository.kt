package com.symbanta.analytics.repositories

import com.symbanta.analytics.domain.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

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
                averageDurationMillis = rs.getDouble("avg_duration_ms"),
            )
        }

    @Transactional
    override fun saveEvent(event: AnalyticsEvent): EventId =
        withLoggingContext(
            "eventId" to event.id.value.toString(),
            "propertyId" to event.propertyId.value.toString(),
            "eventName" to event.eventName,
            "createdAt" to event.createdAt.toString(),
        ) {
            logger.info { "Saving analytics event to database" }

            val params =
                MapSqlParameterSource()
                    .addValue("id", event.id.value)
                    .addValue("propertyId", event.propertyId.value)
                    .addValue("eventName", event.eventName)
                    .addValue("url", event.url)
                    .addValue("referrer", event.referrer)
                    .addValue("screenWidth", event.screenWidth)
                    .addValue("screenHeight", event.screenHeight)
                    .addValue("language", event.language)
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

    private companion object {
        val saveEventSql =
            """
            INSERT INTO events (
                id,
                property_id,
                event_name,
                url,
                referrer,
                screen_width,
                screen_height,
                language,
                props,
                created_at
            ) VALUES (
                :id,
                :propertyId,
                :eventName,
                :url,
                :referrer,
                :screenWidth,
                :screenHeight,
                :language,
                :props::jsonb,
                :createdAt
            )
            """.trimIndent()

        val getStatsSummarySql =
            """
            WITH target_property AS (
                SELECT id
                FROM properties
                WHERE domain = :domain
            )
            SELECT
                COALESCE(COUNT(*) FILTER (WHERE e.event_name = 'page-view'), 0)::bigint AS page_views,
                COALESCE(COUNT(*), 0)::bigint AS visitors,
                0.00::numeric AS bounce_rate,
                0.0::double precision AS avg_duration_ms
            FROM events e
            JOIN target_property tp ON e.property_id = tp.id
            WHERE e.created_at >= :from
              AND e.created_at < :to;
            """.trimIndent()
    }
}
