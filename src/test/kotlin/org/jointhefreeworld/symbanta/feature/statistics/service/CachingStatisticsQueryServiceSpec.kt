/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.jointhefreeworld.symbanta.config.StatisticsCacheConfig
import org.jointhefreeworld.symbanta.feature.statistics.domain.CountryStat
import org.jointhefreeworld.symbanta.feature.statistics.domain.StatsSummary
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.SummaryResult
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.shared.domain.PeriodSelection
import org.jointhefreeworld.symbanta.shared.domain.TimePeriod
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CachingStatisticsQueryServiceSpec :
    FunSpec({

        val queries = mock<AnalyticsQueryPort>()
        val registry = SimpleMeterRegistry()
        val ttl: Duration = Duration.ofSeconds(10)

        var now: Instant = Instant.parse("2026-01-15T00:00:00Z")
        val clock =
            object : ClockPort {
                override fun now(): Instant = now
            }

        val query = StatsQuery(domain = "example.com", period = PeriodSelection(TimePeriod.LAST_7_DAYS))

        fun service(cacheTtl: Duration = ttl) =
            CachingStatisticsQueryService(
                delegate = StatisticsQueryService(queries, clock),
                config = StatisticsCacheConfig(cacheTtl = cacheTtl),
                clock = clock,
                meterRegistry = registry,
            )

        fun summary(pageViews: Long) =
            StatsSummary(
                pageViews = pageViews,
                visitors = 1,
                bounceRate = BigDecimal("10.00"),
                averageDurationMillis = BigDecimal("1000.0"),
            )

        fun counter(name: String): Double = registry.get(name).counter().count()

        beforeTest {
            reset(queries)
            registry.clear()
            now = Instant.parse("2026-01-15T00:00:00Z")
        }

        context("caching") {
            test("serves a repeated read inside the TTL from memory") {
                whenever(queries.summary(any(), any())).thenReturn(summary(1))

                val service = service()
                val first = service.summary(query)
                now = now.plusSeconds(5)

                service.summary(query) shouldBe first
                verify(queries, times(1)).summary(any(), any())
                counter(CachingStatisticsQueryService.METRIC_HITS) shouldBe 1.0
                counter(CachingStatisticsQueryService.METRIC_MISSES) shouldBe 1.0
            }

            test("reads the store again once the TTL has passed") {
                val answers = AtomicInteger()
                whenever(queries.summary(any(), any())).thenAnswer { summary(answers.incrementAndGet().toLong()) }

                val service = service()
                val first = service.summary(query)
                now = now.plusSeconds(10)
                val second = service.summary(query)

                first.summary.pageViews shouldBe 1
                second.summary.pageViews shouldBe 2
                verify(queries, times(2)).summary(any(), any())
            }

            test("computes a cold read once while other requests wait for it") {
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val reads = AtomicInteger()

                whenever(queries.summary(any(), any())).thenAnswer {
                    reads.incrementAndGet()
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    summary(1)
                }

                val service = service()
                val pool = Executors.newFixedThreadPool(2)
                try {
                    val first = pool.submit<SummaryResult> { service.summary(query) }
                    started.await(5, TimeUnit.SECONDS)
                    // Arrives while the first read is still inside the store.
                    val second = pool.submit<SummaryResult> { service.summary(query) }
                    Thread.sleep(100)
                    release.countDown()

                    first.get(5, TimeUnit.SECONDS).summary.pageViews shouldBe 1
                    second.get(5, TimeUnit.SECONDS).summary.pageViews shouldBe 1
                    reads.get() shouldBe 1
                    counter(CachingStatisticsQueryService.METRIC_MISSES) shouldBe 1.0
                    counter(CachingStatisticsQueryService.METRIC_HITS) shouldBe 1.0
                } finally {
                    pool.shutdownNow()
                }
            }

            test("keeps applications and views in separate entries") {
                whenever(queries.summary(any(), any())).thenReturn(summary(1))
                whenever(queries.countries(any(), any())).thenReturn(
                    listOf(CountryStat(country = "NL", pageViews = 1, visitors = 1)),
                )

                val service = service()
                service.summary(query)
                service.summary(StatsQuery(domain = "other.example", period = query.period))
                service.countries(query)
                service.countries(query)

                verify(queries, times(2)).summary(any(), any())
                verify(queries, times(1)).countries(any(), any())
            }

            test("reads every call when the TTL is zero") {
                whenever(queries.summary(any(), any())).thenReturn(summary(1))

                val service = service(cacheTtl = Duration.ZERO)
                service.summary(query)
                service.summary(query)

                verify(queries, times(2)).summary(any(), any())
                counter(CachingStatisticsQueryService.METRIC_HITS) shouldBe 0.0
                counter(CachingStatisticsQueryService.METRIC_MISSES) shouldBe 0.0
            }
        }
    })
