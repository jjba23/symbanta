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

package org.jointhefreeworld.symbanta.benchmark

import org.jointhefreeworld.symbanta.application.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventDispatchPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.application.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.application.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.application.service.EventIngestionService
import org.jointhefreeworld.symbanta.domain.model.AnalyticsEvent
import org.jointhefreeworld.symbanta.domain.model.EventId
import org.jointhefreeworld.symbanta.domain.model.EventType
import org.jointhefreeworld.symbanta.domain.model.EventTypeId
import org.jointhefreeworld.symbanta.domain.model.Property
import org.jointhefreeworld.symbanta.domain.model.PropertyId
import org.jointhefreeworld.symbanta.domain.model.TimePeriod
import org.jointhefreeworld.symbanta.domain.service.ReportingPeriodResolver
import org.jointhefreeworld.symbanta.domain.service.ScreenSizeMapper
import org.jointhefreeworld.symbanta.domain.service.UserAgentParser
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.system.measureNanoTime

/**
 * Dependency-free micro-benchmark harness (issue #3).
 *
 * It measures the pure domain services and the whole application-level ingestion pipeline
 * against in-memory adapters, so the numbers describe Symbanta's own code rather than
 * PostgreSQL or HTTP. End-to-end HTTP throughput is measured separately with k6
 * (`bench/k6/`), which is where database and network costs show up.
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

    // Whole ingestion use case: validation, property lookup, event-type registration (cached),
    // event construction and "persistence" into an in-memory store.
    val service = ingestionPipeline()
    benchmark("EventIngestionService pipeline (in-memory)", iterations = PIPELINE_EVENTS, rounds = 5) { i ->
        service.register(
            RegisterEventCommand(
                domain = "benchmark.example",
                name = if (i % 10 == 0) "signup-click" else "page-view",
                sessionId = "session-${i % 100}",
                url = "https://benchmark.example/article/$i",
                referrer = "https://search.example",
                screenWidth = 1920,
                screenHeight = 1080,
                language = "en-US",
                userAgent = chrome,
                props = mapOf("plan" to "pro", "index" to i),
                country = "NL",
                headers = mapOf("user-agent" to chrome, "accept-language" to "en-US"),
            ),
        )
    }

    println("\nRun `just bench-http` (k6) or `just bench-load-ingest` for end-to-end HTTP numbers.")
}

private const val PIPELINE_EVENTS = 200_000

private fun ingestionPipeline(): EventIngestionService {
    val property =
        Property(
            PropertyId(UUID.randomUUID()),
            "benchmark.example",
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
        )
    val clock =
        object : ClockPort {
            override fun now(): Instant = Instant.parse("2026-01-15T00:00:00Z")
        }

    return EventIngestionService(
        properties =
            object : PropertyRepositoryPort {
                override fun all() = listOf(property)

                override fun byDomain(domain: String) = property.takeIf { it.domain == domain }

                override fun save(property: Property) = Unit
            },
        eventTypes =
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

                override fun listForDomain(domain: String): List<EventType> = registered.values.toList()
            },
        eventStore =
            object : EventStorePort {
                var count = 0L

                override fun save(event: AnalyticsEvent): EventId {
                    count++
                    return event.id
                }
            },
        dispatcher =
            object : EventDispatchPort {
                override fun dispatch(work: () -> Unit) {
                    // Dispatch synchronously so the measured cost is Symbanta's own work.
                    work()
                }
            },
        clock = clock,
    )
}

/**
 * Runs [warmup] discarded iterations, then [rounds] measured rounds of [iterations] calls and
 * prints the fastest round in ops/s (least distorted by GC or scheduling noise).
 */
private fun benchmark(
    name: String,
    iterations: Int,
    warmup: Int = minOf(50_000, iterations / 2),
    rounds: Int = 5,
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
}
