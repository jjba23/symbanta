/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

/**
 * Every statement of the rollup pipeline.
 *
 * The two `...FromEvents` fragments are the single definition of what a rollup row means: the
 * refresher inserts their output, and a read uses the very same fragments for the slices of a
 * window the rollups do not cover yet. The dashboard therefore cannot drift from the rollups,
 * and a fresh deployment (no rollups yet) answers from the identical aggregation.
 *
 * All timestamps are UTC-aligned hours, or local midnight for day rows; per-bucket active time
 * only counts gaps between events *inside* the bucket, and longer windows stitch buckets together
 * with `LAG` at read time.
 */
object RollupSql {
    /** Rollup dimensions, matching the breakdown endpoints. */
    object Dimension {
        const val ROUTE = "route"
        const val COUNTRY = "country"
        const val SCREEN = "screen"
        const val USER_AGENT = "user_agent"
        const val EVENT = "event"
    }

    // ----- events -> rollup rows -----

    fun sessionRowsFromEvents(
        fromParameter: String,
        toParameter: String,
        propertyFilter: String = "",
    ): String =
        """
        SELECT property_id, session_id, bucket,
               COUNT(*) AS events,
               COUNT(*) FILTER (WHERE event_name = :pageView) AS page_views,
               COALESCE(
                   SUM(CASE WHEN gap_us IS NULL OR gap_us > 30000000 THEN 0 ELSE gap_us END),
                   0
               ) AS active_us,
               MIN(created_at) AS first_at,
               MAX(created_at) AS last_at
        FROM (
            SELECT ordered.property_id, ordered.session_id, ordered.bucket, ordered.event_name,
                   ordered.created_at,
                   CASE WHEN ordered.previous_at IS NULL THEN NULL
                        ELSE round(EXTRACT(EPOCH FROM (ordered.created_at - ordered.previous_at)) * 1000000)::bigint
                   END AS gap_us
            FROM (
                SELECT e.property_id,
                       coalesce(e.session_id, '') AS session_id,
                       date_trunc('hour', e.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bucket,
                       e.event_name,
                       e.created_at,
                       LAG(e.created_at) OVER (
                           PARTITION BY e.property_id, coalesce(e.session_id, ''),
                                        date_trunc('hour', e.created_at AT TIME ZONE 'UTC')
                           ORDER BY e.created_at
                       ) AS previous_at
                FROM events e
                WHERE e.created_at >= $fromParameter
                  AND e.created_at < $toParameter
                  $propertyFilter
            ) ordered
        ) gapped
        GROUP BY property_id, session_id, bucket
        """

    fun dimensionRowsFromEvents(
        fromParameter: String,
        toParameter: String,
        propertyFilter: String = "",
    ): String =
        """
        SELECT property_id, dimension, bucket, value, value_is_null, session_id,
               COUNT(*) AS events,
               COUNT(*) FILTER (WHERE event_name = :pageView) AS page_views
        FROM (
            SELECT e.property_id,
                   date_trunc('hour', e.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bucket,
                   coalesce(e.session_id, '') AS session_id,
                   e.event_name,
                   d.dimension,
                   d.value,
                   d.value_is_null
            FROM events e
            JOIN properties p ON p.id = e.property_id
            CROSS JOIN LATERAL (VALUES
                ('${Dimension.ROUTE}', coalesce(regexp_replace(e.url, '^https?://' || p.domain, ''), ''), e.url IS NULL),
                ('${Dimension.COUNTRY}', coalesce(e.country, 'ZZ'), false),
                ('${Dimension.SCREEN}',
                 CASE WHEN e.screen_width IS NULL OR e.screen_height IS NULL THEN NULL
                      ELSE e.screen_width::text || 'x' || e.screen_height::text END,
                 false),
                ('${Dimension.USER_AGENT}', coalesce(e.user_agent, ''), e.user_agent IS NULL),
                ('${Dimension.EVENT}', e.event_name, false)
            ) AS d(dimension, value, value_is_null)
            WHERE e.created_at >= $fromParameter
              AND e.created_at < $toParameter
              $propertyFilter
              AND d.value IS NOT NULL
        ) exploded
        GROUP BY property_id, dimension, bucket, value, value_is_null, session_id
        """

    // ----- rollups, hour -> day -----

    fun sessionDayRows(): String =
        """
        WITH hours AS (
            SELECT property_id, session_id, events, page_views, active_us, first_at, last_at, bucket,
                   date_trunc('day', bucket AT TIME ZONE :zone) AT TIME ZONE :zone AS day
            FROM stats_session_bucket
            WHERE bucket_width = 'hour'
              AND bucket >= :from
              AND bucket < :to
        ),
        ordered AS (
            SELECT hours.*,
                   LAG(last_at) OVER w AS previous_last,
                   LAG(day) OVER w AS previous_day
            FROM hours
            WINDOW w AS (PARTITION BY property_id, session_id ORDER BY bucket)
        )
        SELECT property_id, day, session_id,
               SUM(events) AS events,
               SUM(page_views) AS page_views,
               SUM(active_us) + COALESCE(
                   SUM(CASE WHEN previous_last IS NOT NULL
                             AND previous_day = day
                             AND first_at - previous_last <= INTERVAL '30 seconds'
                            THEN round(EXTRACT(EPOCH FROM (first_at - previous_last)) * 1000000)::bigint
                            ELSE 0 END),
                   0
               ) AS active_us,
               MIN(first_at) AS first_at,
               MAX(last_at) AS last_at
        FROM ordered
        GROUP BY property_id, day, session_id
        """

    fun dimensionDayRows(): String =
        """
        SELECT property_id, dimension, day, value, value_is_null, session_id,
               SUM(events) AS events,
               SUM(page_views) AS page_views
        FROM (
            SELECT property_id, dimension, value, value_is_null, session_id, events, page_views,
                   date_trunc('day', bucket AT TIME ZONE :zone) AT TIME ZONE :zone AS day
            FROM stats_dimension_bucket
            WHERE bucket_width = 'hour'
              AND bucket >= :from
              AND bucket < :to
        ) hours
        GROUP BY property_id, dimension, day, value, value_is_null, session_id
        """

    // ----- maintenance -----

    const val DELETE_SESSION_HOURS =
        "DELETE FROM stats_session_bucket WHERE bucket_width = 'hour' AND bucket >= :from AND bucket < :to"

    fun insertSessionHours(): String =
        """
        INSERT INTO stats_session_bucket
            (property_id, bucket_width, bucket, session_id, events, page_views, active_us, first_at, last_at)
        SELECT property_id, 'hour', bucket, session_id, events, page_views, active_us, first_at, last_at
        FROM (${sessionRowsFromEvents(":from", ":to")}) rows
        """

    const val DELETE_DIMENSION_HOURS =
        "DELETE FROM stats_dimension_bucket WHERE bucket_width = 'hour' AND bucket >= :from AND bucket < :to"

    fun insertDimensionHours(): String =
        """
        INSERT INTO stats_dimension_bucket
            (property_id, dimension, bucket_width, bucket, value, value_is_null, session_id, events, page_views)
        SELECT property_id, dimension, 'hour', bucket, value, value_is_null, session_id, events, page_views
        FROM (${dimensionRowsFromEvents(":from", ":to")}) rows
        """

    const val DELETE_SESSION_DAYS =
        "DELETE FROM stats_session_bucket WHERE bucket_width = 'day' AND bucket >= :from AND bucket < :to"

    fun insertSessionDays(): String =
        """
        INSERT INTO stats_session_bucket
            (property_id, bucket_width, bucket, session_id, events, page_views, active_us, first_at, last_at)
        SELECT property_id, 'day', day, session_id, events, page_views, active_us, first_at, last_at
        FROM (${sessionDayRows()}) rows
        """

    const val DELETE_DIMENSION_DAYS =
        "DELETE FROM stats_dimension_bucket WHERE bucket_width = 'day' AND bucket >= :from AND bucket < :to"

    fun insertDimensionDays(): String =
        """
        INSERT INTO stats_dimension_bucket
            (property_id, dimension, bucket_width, bucket, value, value_is_null, session_id, events, page_views)
        SELECT property_id, dimension, 'day', day, value, value_is_null, session_id, events, page_views
        FROM (${dimensionDayRows()}) rows
        """

    const val COVERAGE = "SELECT covered_through FROM stats_rollup_state WHERE name = 'analytics'"

    const val COVERAGE_FOR_UPDATE = "SELECT covered_through FROM stats_rollup_state WHERE name = 'analytics' FOR UPDATE"

    const val STORE_COVERAGE =
        """
        INSERT INTO stats_rollup_state (name, covered_through) VALUES ('analytics', :coveredThrough)
        ON CONFLICT (name) DO UPDATE SET covered_through = EXCLUDED.covered_through
        """

    const val EARLIEST_EVENT = "SELECT MIN(created_at) FROM events"

    // ----- read: window -> rows -----

    private fun sessionRowSources(): String =
        """
        SELECT property_id, session_id, bucket, events, page_views, active_us, first_at, last_at
        FROM stats_session_bucket
        WHERE property_id = :propertyId
          AND bucket_width = 'day'
          AND bucket >= :dayFrom AND bucket < :dayTo
        UNION ALL
        SELECT property_id, session_id, bucket, events, page_views, active_us, first_at, last_at
        FROM stats_session_bucket
        WHERE property_id = :propertyId
          AND bucket_width = 'hour'
          AND bucket >= :hourFrom AND bucket < :hourTo
          AND NOT (bucket >= :dayFrom AND bucket < :dayTo)
        UNION ALL
        ${sessionRowsFromEvents(":rawBeforeFrom", ":rawBeforeTo", "AND e.property_id = :propertyId")}
        UNION ALL
        ${sessionRowsFromEvents(":rawAfterFrom", ":rawAfterTo", "AND e.property_id = :propertyId")}
        """

    private fun dimensionRowSources(): String =
        """
        SELECT property_id, dimension, bucket, value, value_is_null, session_id, events, page_views
        FROM stats_dimension_bucket
        WHERE property_id = :propertyId
          AND dimension = :dimension
          AND bucket_width = 'day'
          AND bucket >= :dayFrom AND bucket < :dayTo
        UNION ALL
        SELECT property_id, dimension, bucket, value, value_is_null, session_id, events, page_views
        FROM stats_dimension_bucket
        WHERE property_id = :propertyId
          AND dimension = :dimension
          AND bucket_width = 'hour'
          AND bucket >= :hourFrom AND bucket < :hourTo
          AND NOT (bucket >= :dayFrom AND bucket < :dayTo)
        UNION ALL
        ${dimensionRowsFromEvents(":rawBeforeFrom", ":rawBeforeTo", RAW_DIMENSION_FILTER)}
        UNION ALL
        ${dimensionRowsFromEvents(":rawAfterFrom", ":rawAfterTo", RAW_DIMENSION_FILTER)}
        """

    /** The rollup branches filter by dimension too, so the raw slices of a breakdown must as well. */
    private const val RAW_DIMENSION_FILTER = "AND e.property_id = :propertyId AND d.dimension = :dimension"

    fun summary(window: RollupWindow): String =
        """
        WITH rows AS (
            ${sessionRowSources()}
        ),
        stitched AS (
            SELECT session_id, events, page_views, active_us, first_at,
                   LAG(last_at) OVER (PARTITION BY session_id ORDER BY first_at, bucket) AS previous_last
            FROM rows
            WHERE session_id <> ''
        ),
        sessions AS (
            SELECT SUM(page_views) AS pv_count,
                   CASE WHEN SUM(events) > 1
                        THEN SUM(active_us) + COALESCE(
                            SUM(CASE WHEN previous_last IS NOT NULL
                                      AND first_at - previous_last <= INTERVAL '30 seconds'
                                     THEN round(EXTRACT(EPOCH FROM (first_at - previous_last)) * 1000000)::bigint
                                     ELSE 0 END),
                            0
                        )
                   END AS active_us
            FROM stitched
            GROUP BY session_id
        )
        SELECT COALESCE(SUM(pv_count), 0) AS "pageViews",
               COUNT(*) AS "visitors",
               COALESCE(
                   (COUNT(*) FILTER (WHERE pv_count = 1 AND COALESCE(active_us, 0) < 10000000))::numeric
                   / NULLIF(COUNT(*), 0) * 100,
                   0.00
               ) AS "bounceRate",
               COALESCE(AVG(active_us / 1000.0), 0.0)::double precision AS "avgDurationMs"
        FROM sessions
        """

    fun timeSeries(window: RollupWindow): String =
        """
        WITH rows AS (
            ${sessionRowSources()}
        )
        SELECT date_trunc(:granularity, bucket AT TIME ZONE :zone) AT TIME ZONE :zone AS "bucket",
               SUM(page_views) AS "pageViews",
               COUNT(DISTINCT NULLIF(session_id, '')) AS "visitors"
        FROM rows
        GROUP BY 1
        """

    fun breakdown(window: RollupWindow): String =
        """
        WITH rows AS (
            ${dimensionRowSources()}
        )
        SELECT CASE WHEN value_is_null THEN NULL ELSE value END AS "value",
               SUM(page_views) AS "pageViews",
               COUNT(DISTINCT NULLIF(session_id, '')) AS "visitors"
        FROM rows
        GROUP BY 1
        ORDER BY 2 DESC, 1
        """

    fun eventBreakdown(window: RollupWindow): String =
        """
        WITH rows AS (
            ${dimensionRowSources()}
        )
        SELECT value AS "eventName",
               SUM(events) AS "occurrences",
               COUNT(DISTINCT NULLIF(session_id, '')) AS "visitors"
        FROM rows
        GROUP BY 1
        ORDER BY 2 DESC, 1
        """
}
