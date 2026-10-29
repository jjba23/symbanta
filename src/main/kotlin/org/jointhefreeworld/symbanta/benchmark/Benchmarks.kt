/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.benchmark

import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventQueuePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import org.jointhefreeworld.symbanta.feature.events.service.EventIngestionService
import org.jointhefreeworld.symbanta.feature.events.service.EventPersistenceService
import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.feature.statistics.domain.ScreenSizeMapper
import org.jointhefreeworld.symbanta.feature.statistics.domain.UserAgentParser
import org.jointhefreeworld.symbanta.shared.domain.EventId
import org.jointhefreeworld.symbanta.shared.domain.EventTypeId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.jointhefreeworld.symbanta.shared.domain.ReportingPeriodResolver
import org.jointhefreeworld.symbanta.shared.domain.TimePeriod
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.system.measureNanoTime

/**
 * Dependency-free micro-benchmark harness (issue #3).
 *
 * It measures the pure domain services and the application-level ingestion pipeline against
 * in-memory adapters, so the numbers describe Symbanta's own code rather than PostgreSQL or
 * HTTP. End-to-end HTTP throughput is measured separately with k6 (`bench/k6/`), which is where
 * database and network costs show up.
 *
 * Run with `just bench` (or `./mvnw -q compile exec:java -Dexec.mainClass=...`).
 */
fun main() {
    println("symbanta benchmarks — ${Runtime.getRuntime().availableProcessors()} CPUs, JVM ${System.getProperty("java.version")}")
    println("(warmup runs are discarded; the best of the measured rounds is reported)\n")

    val chrome =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"
    val iphone =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    benchmark("UserAgentParser.parse (desktop UA)", iterations = 500_000) { i ->
        UserAgentParser.parse(if (i % 2 == 0) chrome else iphone)
    }

    benchmark("ScreenSizeMapper.mapToBucket", iterations = 2_000_000) { i ->
        ScreenSizeMapper.mapToBucket(1280 + i % 8 * 160, 720 + i % 4 * 180)
    }

    benchmark("ReportingPeriodResolver.resolve (CUSTOM 7d)", iterations = 500_000) {
        ReportingPeriodResolver.resolve(
            TimePeriod.CUSTOM,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 7),
            Instant.parse("2026-01-15T00:00:00Z"),
        )
    }

    benchmark("ReportingPeriodResolver.granularity", iterations = 2_000_000) {
        ReportingPeriodResolver.granularity(
            TimePeriod.LAST_24_HOURS,
            ReportingPeriodResolver.resolve(
                TimePeriod.LAST_24_HOURS,
                null,
                null,
                Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )
    }

    // Ingestion request path: validation + enqueue only, which is what an HTTP request pays.
    val ingestion = EventIngestionService(queue = discardingQueue(), clock = fixedClock)
    benchmark("EventIngestionService validation + enqueue", iterations = PIPELINE_EVENTS, rounds = 5) { i ->
        ingestion.register(command(i, chrome))
    }

    // Queue worker path: batch of MAX_BATCH_SIZE events per call, in-memory store.
    val persistence = EventPersistenceService(inMemoryProperties(), inMemoryEventTypes(), inMemoryStore())
    val batch =
        (0 until BATCH_SIZE).map {
            PendingEvent(EventId(UUID.randomUUID()), Instant.parse("2026-01-15T00:00:00Z"), command(it, chrome))
        }
    benchmark(
        "EventPersistenceService.persist (batch of $BATCH_SIZE)",
        iterations = BATCH_FLUSHES,
        unitsPerIteration = BATCH_SIZE,
        rounds = 5,
    ) {
        persistence.persist(batch)
    }

    println("\nRun `just bench-http` (k6) or `just bench-load-ingest` for end-to-end HTTP numbers.")
}

private const val PIPELINE_EVENTS = 200_000
private const val BATCH_SIZE = 500
private const val BATCH_FLUSHES = 2_000

private val fixedClock =
    object : ClockPort {
        override fun now(): Instant = Instant.parse("2026-01-15T00:00:00Z")
    }

private fun command(
    i: Int,
    userAgent: String,
) = RegisterEventCommand(
    domain = "benchmark.example",
    name = if (i % 10 == 0) "signup-click" else "page-view",
    sessionId = "session-${i % 100}",
    url = "https://benchmark.example/article/$i",
    referrer = "https://search.example",
    screenWidth = 1920,
    screenHeight = 1080,
    language = "en-US",
    userAgent = userAgent,
    props = mapOf("plan" to "pro", "index" to i),
    country = "NL",
    headers = mapOf("user-agent" to userAgent, "accept-language" to "en-US"),
)

private fun discardingQueue() =
    object : EventQueuePort {
        override fun enqueue(event: PendingEvent) = Unit
    }

private fun inMemoryProperties(): PropertyRepositoryPort {
    val property = Property(PropertyId(UUID.randomUUID()), "benchmark.example", Instant.parse("2026-01-01T00:00:00Z"), null)

    return object : PropertyRepositoryPort {
        override fun all() = listOf(property)

        override fun byDomain(domain: String) = property.takeIf { it.domain == domain }

        override fun byDomains(domains: Set<String>) = mapOf(property.domain to property).filterKeys { it in domains }

        override fun save(property: Property) = Unit
    }
}

private fun inMemoryEventTypes(): EventTypeRepositoryPort =
    object : EventTypeRepositoryPort {
        private val registered = mutableMapOf<String, EventType>()

        override fun registerIfAbsent(
            propertyId: PropertyId,
            name: String,
            description: String?,
            now: Instant,
        ): EventType =
            registered.getOrPut(name) {
                EventType(EventTypeId(UUID.randomUUID()), propertyId, name, description, now)
            }

        override fun registerAllIfAbsent(
            propertyId: PropertyId,
            names: Set<String>,
            now: Instant,
        ): List<EventType> = names.map { registerIfAbsent(propertyId, it, null, now) }

        override fun listForDomain(domain: String): List<EventType> = registered.values.toList()
    }

private fun inMemoryStore(): EventStorePort =
    object : EventStorePort {
        var count = 0L

        override fun saveAll(events: List<AnalyticsEvent>): Int {
            count += events.size
            return events.size
        }
    }

/**
 * Runs [warmup] discarded iterations, then [rounds] measured rounds of [iterations] calls and
 * prints the fastest round in ops/s (least distorted by GC or scheduling noise).
 *
 * [unitsPerIteration] lets a call that handles several events report a per-event cost too.
 */
private fun benchmark(
    name: String,
    iterations: Int,
    warmup: Int = minOf(50_000, iterations / 2),
    rounds: Int = 5,
    unitsPerIteration: Int = 1,
    block: (Int) -> Unit,
) {
    repeat(warmup) { block(it) }

    var bestNanos = Long.MAX_VALUE
    repeat(rounds) {
        val elapsed = measureNanoTime { repeat(iterations) { i -> block(i) } }
        if (elapsed < bestNanos) bestNanos = elapsed
    }

    val nanosPerOp = bestNanos.toDouble() / iterations
    val opsPerSecond = 1_000_000_000.0 / nanosPerOp

    println(
        "%-46s %12s ops  %10.1f ns/op  %,14.0f ops/s"
            .format(name, "%,d".format(iterations), nanosPerOp, opsPerSecond),
    )
    if (unitsPerIteration > 1) {
        val nanosPerUnit = nanosPerOp / unitsPerIteration
        println(
            "%-46s %12s     %10.1f ns/event %,14.0f events/s"
                .format("  ↳ per event", "%,d".format(iterations * unitsPerIteration), nanosPerUnit, 1_000_000_000.0 / nanosPerUnit),
        )
    }
}
