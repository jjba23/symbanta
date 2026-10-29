/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.jointhefreeworld.symbanta.config.StatisticsCacheConfig
import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.EventStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.RouteStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeGroupStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentGroupStat
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.BreakdownResult
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetCountryStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetEventStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetRouteStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetScreenSizeStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetStatsSummaryUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetTimeSeriesUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetUserAgentStatsUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.SummaryResult
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.TimeSeriesResult
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-TTL, read-through cache in front of the statistics use cases.
 *
 * A dashboard load asks for seven aggregates over the same application and period, and each one
 * is a full scan of the window, so a refresh — or a second browser tab — repeats work the previous
 * one already did (measured, see README). This decorator answers a repeated read from memory
 * until its TTL window ends and leaves both the application service and the query adapter
 * untouched.
 *
 * Freshness contract: an answer is reused for at most [StatisticsCacheConfig.cacheTtl] after it was
 * computed, so a value can be up to one TTL old and the reported window is the one it was computed
 * with. `cache-ttl: 0` disables the cache and every call reaches the adapter.
 *
 * The first requests for one read are collapsed: the one that wins the per-key lock computes it and
 * the others wait for that answer. Without that, eight browsers opening at once would each run the
 * same full-window aggregation against the store (measured, see README).
 */
class CachingStatisticsQueryService(
    private val delegate: StatisticsQueryService,
    private val config: StatisticsCacheConfig,
    private val clock: ClockPort,
    meterRegistry: MeterRegistry,
) : GetStatsSummaryUseCase,
    GetTimeSeriesUseCase,
    GetRouteStatsUseCase,
    GetScreenSizeStatsUseCase,
    GetUserAgentStatsUseCase,
    GetEventStatsUseCase,
    GetCountryStatsUseCase {
    private val ttlMillis: Long = config.cacheTtl.toMillis()
    private val cache = StatsResultCache()
    private val keyLocks = ConcurrentHashMap<StatsCacheKey, Any>()

    private val hits: Counter =
        Counter
            .builder(METRIC_HITS)
            .description("Stats reads answered from the in-memory cache")
            .register(meterRegistry)

    private val misses: Counter =
        Counter
            .builder(METRIC_MISSES)
            .description("Stats reads that had to reach the analytics store")
            .register(meterRegistry)

    init {
        Gauge
            .builder(METRIC_ENTRIES, cache) { it.size().toDouble() }
            .description("Stats results currently cached")
            .register(meterRegistry)
    }

    override fun summary(query: StatsQuery): SummaryResult = cached(SUMMARY, query) { delegate.summary(query) }

    override fun timeSeries(query: StatsQuery): TimeSeriesResult = cached(TIME_SERIES, query) { delegate.timeSeries(query) }

    override fun screenSizes(query: StatsQuery): BreakdownResult<ScreenSizeGroupStat> =
        cached(SCREEN_SIZES, query) { delegate.screenSizes(query) }

    override fun userAgents(query: StatsQuery): BreakdownResult<UserAgentGroupStat> =
        cached(USER_AGENTS, query) { delegate.userAgents(query) }

    override fun routes(query: StatsQuery): BreakdownResult<RouteStat> = cached(ROUTES, query) { delegate.routes(query) }

    override fun events(query: StatsQuery): BreakdownResult<EventStat> = cached(EVENTS, query) { delegate.events(query) }

    override fun countries(query: StatsQuery): BreakdownResult<CountryStat> = cached(COUNTRIES, query) { delegate.countries(query) }

    private fun <T : Any> cached(
        view: String,
        query: StatsQuery,
        load: () -> T,
    ): T {
        if (ttlMillis <= 0L) return load()

        val key = StatsCacheKey(view, query.domain, query.period)
        cache.get(key, clock.now().toEpochMilli())?.let { cached ->
            hits.increment()
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }

        // One computation per key: a request that arrives while the first one is still running
        // waits here and then finds its answer in the cache, instead of repeating the store read.
        val lock = keyLocks.computeIfAbsent(key) { Any() }
        try {
            synchronized(lock) {
                cache.get(key, clock.now().toEpochMilli())?.let { cached ->
                    hits.increment()
                    @Suppress("UNCHECKED_CAST")
                    return cached as T
                }

                misses.increment()
                return load().also { cache.put(key, it, clock.now().toEpochMilli(), ttlMillis) }
            }
        } finally {
            keyLocks.remove(key, lock)
        }
    }

    companion object {
        private const val SUMMARY = "summary"
        private const val TIME_SERIES = "time-series"
        private const val SCREEN_SIZES = "screen-sizes"
        private const val USER_AGENTS = "user-agents"
        private const val ROUTES = "routes"
        private const val EVENTS = "events"
        private const val COUNTRIES = "countries"

        const val METRIC_HITS = "symbanta.stats.cache.hits"
        const val METRIC_MISSES = "symbanta.stats.cache.misses"
        const val METRIC_ENTRIES = "symbanta.stats.cache.entries"
    }
}
