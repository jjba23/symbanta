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

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Spring Data repository of the event log.
 *
 * Writes go through [org.springframework.data.repository.CrudRepository.saveAll]; the dashboard
 * reads are JPQL aggregations declared here because they all describe the same table. Two queries
 * are native because JPQL cannot express them: the session active-duration window function (`LAG`
 * over each session's events) and the locale-aligned time-series buckets (`date_trunc` in the
 * reporting time zone, which plain JPQL has no way to spell or to keep independent of the
 * database session's `TimeZone`).
 */
interface EventJpaRepository : JpaRepository<EventEntity, UUID> {
    @Query(
        """
        select e from EventEntity e
        where e.name = :name
          and e.propertyId in (select p.id from PropertyEntity p where p.domain = :domain)
        order by e.createdAt desc
        """,
    )
    fun findConsoleEvents(
        @Param("domain") domain: String,
        @Param("name") name: String,
        pageable: Pageable,
    ): List<EventEntity>

    @Query(
        """
        select sum(case when e.name = :pageView then 1 else 0 end) as pageViews,
               count(distinct e.sessionId) as visitors
        from EventEntity e
        where e.propertyId = :propertyId
          and e.createdAt >= :from and e.createdAt < :to
          and e.sessionId is not null
        """,
    )
    fun sessionSummary(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): SessionSummaryRow

    @Query(
        value = """
        WITH session_metrics AS (
            SELECT e.session_id,
                   COUNT(*) FILTER (WHERE e.event_name = :pageView) AS pv_count
            FROM events e
            WHERE e.property_id = :propertyId
              AND e.created_at >= :from AND e.created_at < :to
              AND e.session_id IS NOT NULL
            GROUP BY e.session_id
        ),
        event_deltas AS (
            SELECT e.session_id,
                   EXTRACT(EPOCH FROM (e.created_at - LAG(e.created_at) OVER (PARTITION BY e.session_id ORDER BY e.created_at))) AS gap_sec
            FROM events e
            WHERE e.property_id = :propertyId
              AND e.created_at >= :from AND e.created_at <= :to
              AND e.session_id IS NOT NULL
        ),
        active_durations AS (
            SELECT session_id,
                   SUM(CASE WHEN gap_sec <= 30 THEN gap_sec * 1000 ELSE 0 END) AS active_ms
            FROM event_deltas
            WHERE gap_sec IS NOT NULL
            GROUP BY session_id
        )
        SELECT
            COALESCE(
                (COUNT(*) FILTER (WHERE sm.pv_count = 1 AND COALESCE(ad.active_ms, 0) < 10000)::numeric /
                 NULLIF(COUNT(sm.session_id), 0)) * 100,
                0.00
            )::numeric AS bounce_rate,
            COALESCE(AVG(ad.active_ms), 0.0)::double precision AS avg_duration_ms
        FROM session_metrics sm
        LEFT JOIN active_durations ad ON sm.session_id = ad.session_id
        """,
        nativeQuery = true,
    )
    fun sessionBehaviour(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): SessionBehaviourRow

    /**
     * Buckets by local wall-clock hour/day in `zone`: truncating a `timestamptz` directly would
     * use the database session's `TimeZone`, which is not necessarily the reporting time zone and
     * made every bucket key miss the ones the adapter generates.
     */
    @Query(
        value = """
        SELECT date_trunc(:granularity, e.created_at AT TIME ZONE :zone) AT TIME ZONE :zone AS "bucket",
               COUNT(*) FILTER (WHERE e.event_name = :pageView) AS "pageViews",
               COUNT(DISTINCT e.session_id) AS "visitors"
        FROM events e
        WHERE e.property_id = :propertyId
          AND e.created_at >= :from AND e.created_at < :to
        GROUP BY 1
        """,
        nativeQuery = true,
    )
    fun timeSeries(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("granularity") granularity: String,
        @Param("zone") zone: String,
        @Param("pageView") pageView: String,
    ): List<TimeSeriesRow>

    @Query(
        """
        select e.screenWidth as width,
               e.screenHeight as height,
               sum(case when e.name = :pageView then 1 else 0 end) as pageViews,
               count(distinct e.sessionId) as visitors
        from EventEntity e
        where e.propertyId = :propertyId
          and e.createdAt >= :from and e.createdAt <= :to
          and e.screenWidth is not null
          and e.screenHeight is not null
        group by e.screenWidth, e.screenHeight
        order by sum(case when e.name = :pageView then 1 else 0 end) desc
        """,
    )
    fun screenSizes(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): List<ScreenSizeRow>

    @Query(
        """
        select e.userAgent as userAgent,
               sum(case when e.name = :pageView then 1 else 0 end) as pageViews,
               count(distinct e.sessionId) as visitors
        from EventEntity e
        where e.propertyId = :propertyId
          and e.createdAt >= :from and e.createdAt <= :to
        group by e.userAgent
        order by sum(case when e.name = :pageView then 1 else 0 end) desc
        """,
    )
    fun userAgents(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): List<UserAgentRow>

    @Query(
        """
        select function('regexp_replace', e.url, concat('^https?://', cast(:domain as string)), '') as route,
               sum(case when e.name = :pageView then 1 else 0 end) as pageViews,
               count(distinct e.sessionId) as visitors
        from EventEntity e
        where e.propertyId = :propertyId
          and e.createdAt >= :from and e.createdAt <= :to
        group by 1
        order by sum(case when e.name = :pageView then 1 else 0 end) desc
        """,
    )
    fun routes(
        @Param("propertyId") propertyId: UUID,
        @Param("domain") domain: String,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): List<RouteRow>

    @Query(
        """
        select e.name as eventName,
               count(e) as occurrences,
               count(distinct e.sessionId) as visitors
        from EventEntity e
        where e.propertyId = :propertyId
          and e.createdAt >= :from and e.createdAt <= :to
        group by e.name
        order by count(e) desc, e.name asc
        """,
    )
    fun events(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<EventCountRow>

    @Query(
        """
        select coalesce(e.country, 'ZZ') as country,
               sum(case when e.name = :pageView then 1 else 0 end) as pageViews,
               count(distinct e.sessionId) as visitors
        from EventEntity e
        where e.propertyId = :propertyId
          and e.createdAt >= :from and e.createdAt <= :to
        group by coalesce(e.country, 'ZZ')
        order by sum(case when e.name = :pageView then 1 else 0 end) desc
        """,
    )
    fun countries(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): List<CountryRow>
}

interface SessionSummaryRow {
    val pageViews: Long?
    val visitors: Long
}

interface SessionBehaviourRow {
    val bounceRate: BigDecimal
    val avgDurationMs: Double
}

interface TimeSeriesRow {
    val bucket: Instant
    val pageViews: Long?
    val visitors: Long
}

interface ScreenSizeRow {
    val width: Int
    val height: Int
    val pageViews: Long?
    val visitors: Long
}

interface UserAgentRow {
    val userAgent: String?
    val pageViews: Long?
    val visitors: Long
}

interface RouteRow {
    val route: String?
    val pageViews: Long?
    val visitors: Long
}

interface EventCountRow {
    val eventName: String
    val occurrences: Long
    val visitors: Long
}

interface CountryRow {
    val country: String
    val pageViews: Long?
    val visitors: Long
}
