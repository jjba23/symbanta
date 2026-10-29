/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.adapter.outbound.queue

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.jointhefreeworld.symbanta.feature.events.port.inbound.EventPersistenceUseCase
import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import org.jointhefreeworld.symbanta.shared.domain.EventId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BatchingEventDispatcherSpec :
    FunSpec({

        lateinit var registry: SimpleMeterRegistry

        beforeTest {
            registry = SimpleMeterRegistry()
        }

        fun pending(index: Int) =
            PendingEvent(
                eventId = EventId(UUID.randomUUID()),
                receivedAt = Instant.parse("2026-01-15T00:00:00Z"),
                command =
                    RegisterEventCommand(
                        domain = "example.com",
                        name = "page-view",
                        sessionId = "session-$index",
                        url = "https://example.com/",
                        referrer = null,
                        screenWidth = 1920,
                        screenHeight = 1080,
                        language = "en-US",
                        userAgent = "Mozilla/5.0",
                        props = mapOf("index" to index),
                        country = "NL",
                        headers = emptyMap(),
                    ),
            )

        fun counter(name: String): Double = registry.get(name).counter().count()

        fun backlog(): Double = registry.get(BatchingEventDispatcher.METRIC_BACKLOG).gauge().value()

        context("coalescing") {
            test("drains a backlog into one batched write per worker pass") {
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)
                val batches = CopyOnWriteArrayList<List<PendingEvent>>()

                val persistence =
                    object : EventPersistenceUseCase {
                        override fun persist(events: List<PendingEvent>): Int {
                            batches += events
                            if (batches.size == 1) {
                                started.countDown()
                                release.await(5, TimeUnit.SECONDS)
                            }
                            return events.size
                        }
                    }

                val dispatcher =
                    BatchingEventDispatcher(
                        persistence = persistence,
                        meterRegistry = registry,
                        queueCapacity = 100,
                        workerCount = 1,
                        maxBatchSize = 100,
                    )

                try {
                    dispatcher.enqueue(pending(0))
                    await { started.await(0, TimeUnit.MILLISECONDS) }
                    await { backlog() == 1.0 }

                    repeat(50) { dispatcher.enqueue(pending(it + 1)) }
                    await { backlog() == 51.0 }

                    release.countDown()
                    await { counter(BatchingEventDispatcher.METRIC_PROCESSED) == 51.0 }

                    batches.map { it.size } shouldContainExactly listOf(1, 50)
                    counter(BatchingEventDispatcher.METRIC_ENQUEUED) shouldBe 51.0
                    backlog() shouldBe 0.0
                    registry.get(BatchingEventDispatcher.METRIC_BATCH_SIZE).summary().count() shouldBe 2L
                    registry.get(BatchingEventDispatcher.METRIC_FLUSH).timer().count() shouldBe 2L
                    counter(BatchingEventDispatcher.METRIC_DROPPED) shouldBe 0.0
                } finally {
                    release.countDown()
                    dispatcher.shutdown()
                }
            }
        }

        context("failures") {
            test("counts a failed batch and still releases its backlog") {
                val dispatcher =
                    BatchingEventDispatcher(
                        persistence =
                            object : EventPersistenceUseCase {
                                override fun persist(events: List<PendingEvent>): Int = error("database is down")
                            },
                        meterRegistry = registry,
                        queueCapacity = 100,
                        workerCount = 1,
                        maxBatchSize = 10,
                    )

                try {
                    repeat(3) { dispatcher.enqueue(pending(it)) }

                    await { counter(BatchingEventDispatcher.METRIC_FAILED) == 3.0 }

                    counter(BatchingEventDispatcher.METRIC_PROCESSED) shouldBe 0.0
                    backlog() shouldBe 0.0
                } finally {
                    dispatcher.shutdown()
                }
            }
        }

        context("overload") {
            test("drops events once the queue is full and reports them as dropped") {
                val started = CountDownLatch(1)
                val release = CountDownLatch(1)

                val dispatcher =
                    BatchingEventDispatcher(
                        persistence =
                            object : EventPersistenceUseCase {
                                override fun persist(events: List<PendingEvent>): Int {
                                    started.countDown()
                                    release.await(5, TimeUnit.SECONDS)
                                    return events.size
                                }
                            },
                        meterRegistry = registry,
                        queueCapacity = 1,
                        workerCount = 1,
                        maxBatchSize = 1,
                    )

                try {
                    dispatcher.enqueue(pending(0))
                    await { started.await(0, TimeUnit.MILLISECONDS) }

                    // One slot is free: accepted. The next one has nowhere to go.
                    dispatcher.enqueue(pending(1))
                    dispatcher.enqueue(pending(2))

                    counter(BatchingEventDispatcher.METRIC_DROPPED) shouldBe 1.0
                    backlog() shouldBeGreaterThanOrEqual 1.0
                    counter(BatchingEventDispatcher.METRIC_ENQUEUED) shouldBe 2.0
                } finally {
                    release.countDown()
                    dispatcher.shutdown()
                }

                await { counter(BatchingEventDispatcher.METRIC_PROCESSED) == 2.0 }
                backlog() shouldBe 0.0
            }
        }
    })

/** Polls [condition] until it holds, failing the test instead of hanging. */
private fun await(
    timeoutMillis: Long = 5_000,
    condition: () -> Boolean,
) {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)

    while (!condition()) {
        if (System.nanoTime() > deadline) error("condition was not met within ${timeoutMillis}ms")
        Thread.sleep(5)
    }
}
