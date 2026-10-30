/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeBuckets
import org.jointhefreeworld.symbanta.shared.domain.ReportingPeriodResolver
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Maintains the pre-aggregated analytics tables.
 *
 * Each run rebuilds the buckets whose contents can still change and advances the coverage
 * watermark, so a dashboard read never has to look at the event log except for the slices the
 * rollups do not cover yet (see [RollupWindow]).
 *
 * A run is one transaction: readers keep seeing the previous rows until the new ones are committed,
 * so a bucket is never observed half-built.
 *
 * Buckets are rebuilt rather than updated in place, which also repairs late arrivals: an event is
 * timestamped when it is ingested and committed a few milliseconds later, so a batch can land after
 * the bucket it belongs to was built. The tick that closes an hour rebuilds that whole hour again.
 */
class StatsRollupRefresher(
    private val jdbc: NamedParameterJdbcTemplate,
    private val clock: ClockPort,
    transactionManager: PlatformTransactionManager,
    private val zone: ZoneId = ReportingPeriodResolver.ZONE_ID,
) {
    private val logger = KotlinLogging.logger {}

    // An explicit template rather than @Transactional: the refresh calls its own helpers, and a
    // proxy would leave them outside the transaction (or, worse, run on a proxy instance with no
    // injected state).
    private val transactions = TransactionTemplate(transactionManager)

    /**
     * Brings the rollups up to "now" and returns the instant they are valid through.
     *
     * Everything before the returned instant is represented in the rollup tables; the current
     * hour is represented as of this run, which is why the watermark is a plain instant and not an
     * hour boundary.
     */
    fun refresh(): Instant = transactions.execute { refreshInTransaction() }

    private fun refreshInTransaction(): Instant {
        val startedAt = clock.now()
        val covered = lockCoverage()
        val currentHour = TimeBuckets.floorToHour(startedAt)

        // Catch up on every whole hour since the last run (or since the first event ever, which is
        // the backfill a deployment runs once after the migration).
        val start = covered?.let { TimeBuckets.floorToHour(it) } ?: earliestEventHour() ?: currentHour
        val caughtUpFrom = start.coerceAtMost(currentHour)
        if (caughtUpFrom.isBefore(currentHour)) {
            rebuildHours(caughtUpFrom, currentHour)
            logger.info {
                "Rolled up analytics from $caughtUpFrom to $currentHour (" +
                    "${ChronoUnit.HOURS.between(caughtUpFrom, currentHour)} hour buckets)"
            }
        }

        // The hour in progress is rebuilt on every run so "today" stays close to real time.
        rebuildHours(currentHour, currentHour.plus(1, ChronoUnit.HOURS))

        rebuildDays(
            TimeBuckets.floorToLocalMidnight(caughtUpFrom, zone),
            nextLocalMidnight(startedAt),
        )

        // The watermark is taken after the builds: rows may contain an event that was committed
        // while they were being read, and a reader that trusted `startedAt` would then also pick
        // that event up from the raw tail. Ending the coverage later can only miss an event (fixed
        // by the next rebuild of its bucket), never count one twice.
        // Truncated to the precision the column stores, so a read sees the same instant this
        // call returns instead of one that was rounded on the way to PostgreSQL.
        val coveredThrough = clock.now().truncatedTo(ChronoUnit.MICROS)
        storeCoverage(coveredThrough)
        logger.debug { "Analytics rollups cover everything before $coveredThrough" }
        return coveredThrough
    }

    /** Instant the rollups are complete through, or null when nothing has been rolled up yet. */
    fun coveredThrough(): Instant? =
        try {
            jdbc
                .queryForObject(RollupSql.COVERAGE, NO_PARAMETERS, OffsetDateTime::class.java)
                ?.toInstant()
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    // ----- buckets -----

    private fun rebuildHours(
        from: Instant,
        to: Instant,
    ) {
        if (!from.isBefore(to)) return

        val parameters = bounds(from, to)
        jdbc.update(RollupSql.DELETE_SESSION_HOURS, parameters)
        jdbc.update(RollupSql.insertSessionHours(), parameters + PAGE_VIEW)
        jdbc.update(RollupSql.DELETE_DIMENSION_HOURS, parameters)
        jdbc.update(RollupSql.insertDimensionHours(), parameters + PAGE_VIEW)
    }

    private fun rebuildDays(
        from: Instant,
        to: Instant,
    ) {
        if (!from.isBefore(to)) return

        val parameters = bounds(from, to) + mapOf("zone" to zone.id)
        jdbc.update(RollupSql.DELETE_SESSION_DAYS, parameters)
        jdbc.update(RollupSql.insertSessionDays(), parameters)
        jdbc.update(RollupSql.DELETE_DIMENSION_DAYS, parameters)
        jdbc.update(RollupSql.insertDimensionDays(), parameters)
    }

    // ----- state -----

    private fun lockCoverage(): Instant? =
        try {
            jdbc
                .queryForObject(RollupSql.COVERAGE_FOR_UPDATE, NO_PARAMETERS, OffsetDateTime::class.java)
                ?.toInstant()
        } catch (_: EmptyResultDataAccessException) {
            null
        }

    private fun storeCoverage(instant: Instant) {
        jdbc.update(RollupSql.STORE_COVERAGE, mapOf("coveredThrough" to instant.atOffset(ZoneOffset.UTC)))
    }

    private fun earliestEventHour(): Instant? =
        jdbc
            .queryForObject(RollupSql.EARLIEST_EVENT, NO_PARAMETERS, OffsetDateTime::class.java)
            ?.toInstant()
            ?.let(TimeBuckets::floorToHour)

    private fun bounds(
        from: Instant,
        to: Instant,
    ): Map<String, Any> =
        mapOf(
            "from" to from.atOffset(ZoneOffset.UTC),
            "to" to to.atOffset(ZoneOffset.UTC),
        )

    /** Local midnight that starts the day after [instant], skipping DST transitions safely. */
    private fun nextLocalMidnight(instant: Instant): Instant =
        instant
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()

    private companion object {
        val NO_PARAMETERS: Map<String, Any> = emptyMap()

        val PAGE_VIEW = mapOf("pageView" to EventNames.PAGE_VIEW)
    }
}
