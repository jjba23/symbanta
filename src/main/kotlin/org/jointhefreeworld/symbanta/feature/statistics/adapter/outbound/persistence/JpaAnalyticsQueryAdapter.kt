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
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.EventJpaRepository
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Analytics read port implemented on top of Spring Data JPA.
 *
 * Property lookup goes through the properties port (the published contract of that feature), and
 * every aggregate is a JPQL query on [EventJpaRepository]; the domain's event window is resolved
 * once per call and an unknown domain yields zeroes/empty lists, exactly like the previous SQL.
 *
 * Time-series buckets are generated in Kotlin and merged with the grouped rows so the result is
 * zero-filled even for windows without traffic.
 */
open class JpaAnalyticsQueryAdapter(
    private val properties: PropertyRepositoryPort,
    private val events: EventJpaRepository,
) : AnalyticsQueryPort {
    private val logger = KotlinLogging.logger {}

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

            val property = properties.byDomain(domain) ?: return@withLoggingContext EMPTY_SUMMARY
            val counts = events.sessionSummary(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            val behaviour = events.sessionBehaviour(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)

            StatsSummary(
                pageViews = counts?.pageViews ?: 0,
                visitors = counts?.visitors ?: 0,
                bounceRate = behaviour?.bounceRate ?: BigDecimal.ZERO,
                averageDurationMillis = BigDecimal.valueOf(behaviour?.avgDurationMs ?: 0.0),
            )
        }

    @Transactional(readOnly = true)
    override fun timeSeries(
        domain: String,
        range: DateRange,
        granularity: TimeGranularity,
    ): List<TimeSeriesPoint> {
        logger.debug { "Fetching time-series analytics stats from database" }

        val buckets = bucketsOf(range, granularity)
        val property = properties.byDomain(domain)

        val counts =
            if (property == null) {
                emptyMap()
            } else {
                events
                    .timeSeries(property.id.value, range.from, range.to, granularity.unit, EventNames.PAGE_VIEW)
                    .associate { it.bucket to it }
            }

        return buckets.map { bucket ->
            val row = counts[bucket]
            TimeSeriesPoint(
                timestamp = bucket,
                pageViews = row?.pageViews ?: 0,
                visitors = row?.visitors ?: 0,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun screenSizes(
        domain: String,
        range: DateRange,
    ): List<ScreenSizeStat> {
        logger.debug { "Fetching screen sizes breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .screenSizes(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            .map { ScreenSizeStat(width = it.width, height = it.height, pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun userAgents(
        domain: String,
        range: DateRange,
    ): List<UserAgentStat> {
        logger.debug { "Fetching user agents breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .userAgents(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            .map { UserAgentStat(userAgent = it.userAgent ?: "???", pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun routes(
        domain: String,
        range: DateRange,
    ): List<RouteStat> {
        logger.debug { "Fetching routes breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .routes(property.id.value, domain, range.from, range.to, EventNames.PAGE_VIEW)
            .map { RouteStat(route = it.route ?: "???", pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun events(
        domain: String,
        range: DateRange,
    ): List<EventStat> {
        logger.debug { "Fetching event type breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .events(property.id.value, range.from, range.to)
            .map { EventStat(eventName = it.eventName, occurrences = it.occurrences, visitors = it.visitors) }
    }

    @Transactional(readOnly = true)
    override fun countries(
        domain: String,
        range: DateRange,
    ): List<CountryStat> {
        logger.debug { "Fetching country breakdown from database" }

        val property = properties.byDomain(domain) ?: return emptyList()

        return events
            .countries(property.id.value, range.from, range.to, EventNames.PAGE_VIEW)
            .map { CountryStat(country = it.country, pageViews = it.pageViews ?: 0, visitors = it.visitors) }
    }

    private fun bucketsOf(
        range: DateRange,
        granularity: TimeGranularity,
    ): List<Instant> {
        val unit = if (granularity == TimeGranularity.HOUR) ChronoUnit.HOURS else ChronoUnit.DAYS
        val step = if (granularity == TimeGranularity.HOUR) 1L else 24L

        val buckets = mutableListOf<Instant>()
        var cursor = range.from.truncatedTo(unit)
        val last = range.to.truncatedTo(unit)

        while (!cursor.isAfter(last)) {
            buckets += cursor
            cursor = cursor.plus(step, ChronoUnit.HOURS)
        }

        return buckets
    }

    private companion object {
        val EMPTY_SUMMARY = StatsSummary(pageViews = 0, visitors = 0, bounceRate = BigDecimal.ZERO, averageDurationMillis = BigDecimal.ZERO)
    }
}
