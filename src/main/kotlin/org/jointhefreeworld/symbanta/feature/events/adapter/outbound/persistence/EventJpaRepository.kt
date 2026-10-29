/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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

    /**
     * Page views, visitors, bounce rate and average active duration in one pass.
     *
     * The dashboard used to ask for the counts and the session behaviour separately, which read
     * the same window twice: a page view count, then a window function pass to pair each event
     * with the previous one in its session. Both now come from a single scan: the sessions CTE
     * pairs the events once (`LAG`), and everything else is an aggregate over its rows. Page views
     * and visitors keep the half-open `[from, to)` bound; the gap that measures active time also
     * admits an event exactly at `to`, as before, so a session's last interval is not lost.
     */
    @Query(
        value = """
        WITH session_events AS (
            SELECT e.session_id,
                   e.event_name,
                   e.created_at,
                   EXTRACT(EPOCH FROM (e.created_at - LAG(e.created_at) OVER (PARTITION BY e.session_id ORDER BY e.created_at))) AS gap_sec
            FROM events e
            WHERE e.property_id = :propertyId
              AND e.created_at >= :from AND e.created_at <= :to
              AND e.session_id IS NOT NULL
        ),
        sessions AS (
            SELECT session_id,
                   COUNT(*) FILTER (WHERE event_name = :pageView AND created_at < :to) AS pv_count,
                   SUM(CASE WHEN gap_sec <= 30 THEN gap_sec * 1000 ELSE 0 END) FILTER (WHERE gap_sec IS NOT NULL) AS active_ms
            FROM session_events
            GROUP BY session_id
            HAVING COUNT(*) FILTER (WHERE created_at < :to) > 0
        )
        SELECT
            COALESCE(SUM(s.pv_count), 0) AS "pageViews",
            COUNT(s.session_id) AS "visitors",
            COALESCE(
                (COUNT(*) FILTER (WHERE s.pv_count = 1 AND COALESCE(s.active_ms, 0) < 10000)::numeric /
                 NULLIF(COUNT(s.session_id), 0)) * 100,
                0.00
            )::numeric AS "bounceRate",
            COALESCE(AVG(s.active_ms), 0.0)::double precision AS "avgDurationMs"
        FROM sessions s
        """,
        nativeQuery = true,
    )
    fun sessionSummary(
        @Param("propertyId") propertyId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("pageView") pageView: String,
    ): SessionSummaryRow

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
