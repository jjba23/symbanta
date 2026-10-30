/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.EventStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.RouteStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.StatsSummary
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeBuckets
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeSeriesPoint
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentStat
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.shared.domain.DateRange
import org.jointhefreeworld.symbanta.shared.domain.ReportingPeriodResolver
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Analytics read port answered from the pre-aggregated rollups.
 *
 * Each request is split into the slices the rollups already cover (day rows for whole days, hour
 * rows for the rest) and the slices they do not — the part of the first hour before the window
 * starts, and whatever the refresher has not caught up with yet. Only those slices touch the event
 * log, so a dashboard read is proportional to sessions per bucket rather than events per window.
 *
 * The aggregation itself is the same in both cases because [RollupSql] defines one "events to rows"
 * mapping; see [RollupWindow] for the split.
 */
open class RollupAnalyticsQueryAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
    private val properties: PropertyRepositoryPort,
    /** Source of the coverage watermark: it owns the rollup state, so reads ask it how far to trust. */
    private val rollups: StatsRollupRefresher,
) : AnalyticsQueryPort {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun summary(
        domain: String,
        range: DateRange,
    ): StatsSummary {
        val property = properties.byDomain(domain) ?: return EMPTY_SUMMARY
        val window = window(range, wholeDays = true)

        logger.debug { "Reading rolled-up analytics summary for $domain over $range" }

        return jdbc.query(
            RollupSql.summary(window),
            parameters(property.id.value, window),
            ResultSetExtractor { rs ->
                if (!rs.next()) {
                    EMPTY_SUMMARY
                } else {
                    StatsSummary(
                        pageViews = rs.getLong("pageViews"),
                        visitors = rs.getLong("visitors"),
                        bounceRate = rs.getBigDecimal("bounceRate"),
                        averageDurationMillis = BigDecimal.valueOf(rs.getDouble("avgDurationMs")),
                    )
                }
            },
        )
    }

    @Transactional(readOnly = true)
    override fun timeSeries(
        domain: String,
        range: DateRange,
        granularity: TimeGranularity,
    ): List<TimeSeriesPoint> {
        val property = properties.byDomain(domain)
        // Session behaviour and bucketing are always computed over the same window, so the
        // chart's first bucket starts where the query does.
        val bounded = TimeBuckets.bounded(range, ReportingPeriodResolver.ZONE_ID, granularity)

        val counts = mutableMapOf<Instant, TimeSeriesPoint>()
        if (property != null) {
            val window = window(bounded, wholeDays = granularity == TimeGranularity.DAY)
            logger.debug { "Reading rolled-up analytics time series for $domain over $range at $granularity" }

            jdbc.query(
                RollupSql.timeSeries(window),
                parameters(property.id.value, window)
                    .addValue("granularity", granularity.unit)
                    .addValue("zone", ReportingPeriodResolver.TIMEZONE),
                RowCallbackHandler { rs ->
                    val bucket = instant(rs, 1)
                    counts[bucket] = TimeSeriesPoint(timestamp = bucket, pageViews = rs.getLong(2), visitors = rs.getLong(3))
                },
            )
        }

        return TimeBuckets
            .of(bounded, ReportingPeriodResolver.ZONE_ID, granularity)
            .map { bucket -> counts[bucket] ?: TimeSeriesPoint(timestamp = bucket, pageViews = 0, visitors = 0) }
    }

    @Transactional(readOnly = true)
    override fun screenSizes(
        domain: String,
        range: DateRange,
    ): List<ScreenSizeStat> {
        val property = properties.byDomain(domain) ?: return emptyList()

        // Screen rows always carry both dimensions (the rollup skips events without them), so the
        // only nullable case here is a row that should not exist.
        return breakdown(range, property.id.value, RollupSql.Dimension.SCREEN).mapNotNull { row ->
            val size = row.value ?: return@mapNotNull null
            ScreenSizeStat(
                width = size.substringBefore('x').toInt(),
                height = size.substringAfter('x').toInt(),
                pageViews = row.pageViews,
                visitors = row.visitors,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun userAgents(
        domain: String,
        range: DateRange,
    ): List<UserAgentStat> {
        val property = properties.byDomain(domain) ?: return emptyList()

        return breakdown(range, property.id.value, RollupSql.Dimension.USER_AGENT).map { row ->
            UserAgentStat(userAgent = row.value ?: "???", pageViews = row.pageViews, visitors = row.visitors)
        }
    }

    @Transactional(readOnly = true)
    override fun routes(
        domain: String,
        range: DateRange,
    ): List<RouteStat> {
        val property = properties.byDomain(domain) ?: return emptyList()

        return breakdown(range, property.id.value, RollupSql.Dimension.ROUTE).map { row ->
            RouteStat(route = row.value ?: "???", pageViews = row.pageViews, visitors = row.visitors)
        }
    }

    @Transactional(readOnly = true)
    override fun events(
        domain: String,
        range: DateRange,
    ): List<EventStat> {
        val property = properties.byDomain(domain) ?: return emptyList()
        val window = window(range, wholeDays = true)

        return jdbc.query(
            RollupSql.eventBreakdown(window),
            parameters(property.id.value, window).addValue("dimension", RollupSql.Dimension.EVENT),
            RowMapper { rs, _ ->
                EventStat(
                    eventName = rs.getString("eventName"),
                    occurrences = rs.getLong("occurrences"),
                    visitors = rs.getLong("visitors"),
                )
            },
        )
    }

    @Transactional(readOnly = true)
    override fun countries(
        domain: String,
        range: DateRange,
    ): List<CountryStat> {
        val property = properties.byDomain(domain) ?: return emptyList()

        return breakdown(range, property.id.value, RollupSql.Dimension.COUNTRY).map { row ->
            CountryStat(country = row.value ?: "ZZ", pageViews = row.pageViews, visitors = row.visitors)
        }
    }

    private fun breakdown(
        range: DateRange,
        propertyId: UUID,
        dimension: String,
    ): List<BreakdownRow> {
        val window = window(range, wholeDays = true)

        return jdbc.query(
            RollupSql.breakdown(window),
            parameters(propertyId, window).addValue("dimension", dimension),
            RowMapper { rs, _ ->
                BreakdownRow(
                    value = rs.getString("value"),
                    pageViews = rs.getLong("pageViews"),
                    visitors = rs.getLong("visitors"),
                )
            },
        )
    }

    private fun window(
        range: DateRange,
        wholeDays: Boolean,
    ): RollupWindow =
        RollupWindow.of(
            range = range,
            coveredThrough = rollups.coveredThrough(),
            zone = ReportingPeriodResolver.ZONE_ID,
            wholeDays = wholeDays,
        )

    private fun parameters(
        propertyId: UUID,
        window: RollupWindow,
    ): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("propertyId", propertyId)
            .addValue("dayFrom", window.dayRows.from.atOffset(ZoneOffset.UTC))
            .addValue("dayTo", window.dayRows.to.atOffset(ZoneOffset.UTC))
            .addValue("hourFrom", window.hourRows.from.atOffset(ZoneOffset.UTC))
            .addValue("hourTo", window.hourRows.to.atOffset(ZoneOffset.UTC))
            .addValue("rawBeforeFrom", window.rawBefore.from.atOffset(ZoneOffset.UTC))
            .addValue("rawBeforeTo", window.rawBefore.to.atOffset(ZoneOffset.UTC))
            .addValue("rawAfterFrom", window.rawAfter.from.atOffset(ZoneOffset.UTC))
            .addValue("rawAfterTo", window.rawAfter.to.atOffset(ZoneOffset.UTC))
            // Only the raw slices use it, but the SQL is one shared shape, so it is always bound.
            .addValue("pageView", EventNames.PAGE_VIEW)

    private fun instant(
        rs: java.sql.ResultSet,
        column: Int,
    ): Instant = rs.getObject(column, OffsetDateTime::class.java).toInstant()

    private data class BreakdownRow(
        val value: String?,
        val pageViews: Long,
        val visitors: Long,
    )

    private companion object {
        val EMPTY_SUMMARY =
            StatsSummary(
                pageViews = 0,
                visitors = 0,
                bounceRate = BigDecimal.ZERO,
                averageDurationMillis = BigDecimal.ZERO,
            )
    }
}
