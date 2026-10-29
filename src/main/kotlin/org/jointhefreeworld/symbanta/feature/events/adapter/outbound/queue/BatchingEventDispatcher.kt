/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.adapter.outbound.queue

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jointhefreeworld.symbanta.feature.events.port.inbound.EventPersistenceUseCase
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventQueuePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded, in-process event queue used by the HTTP ingestion adapter.
 *
 * Ingestion must never block an HTTP thread on the database, but an unbounded `launch` per
 * request lets a burst of traffic exhaust the JDBC pool and starve the very threads that should
 * stay responsive (measured while benchmarking, see README). This adapter therefore uses:
 *
 * - a bounded [Channel] that absorbs short bursts,
 * - a fixed pool of worker coroutines that is the only component touching the database,
 * - coalescing: a worker drains up to [maxBatchSize] queued events, waiting at most
 *   [lingerMillis] for more of them, and writes them with a single batched statement. Without the
 *   wait a worker flushes whatever happens to be queued, and at a few thousand events per second
 *   that is one event per transaction: the commit, not the row, is the cost (measured, see
 *   README),
 * - a drop-with-warning policy once the queue is full: the API keeps answering 202 and the
 *   dropped count is logged (rate limited) instead of growing the heap without bound.
 *
 * Overload is observable through Micrometer: see the `symbanta.ingestion.*` meters below, which
 * are exported by Actuator's Prometheus endpoint.
 */
class BatchingEventDispatcher(
    private val persistence: EventPersistenceUseCase,
    meterRegistry: MeterRegistry,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    workerCount: Int = DEFAULT_WORKER_COUNT,
    private val maxBatchSize: Int = DEFAULT_MAX_BATCH_SIZE,
    private val lingerMillis: Long = DEFAULT_LINGER_MILLIS,
) : EventQueuePort {
    private val queueCapacity: Int = queueCapacity
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<PendingEvent>(queueCapacity)
    private val workers: List<Job>

    /** Events accepted but not yet written (or abandoned): the queue backlog. */
    private val backlog = AtomicLong()

    private val dropCounter = AtomicLong()

    private val enqueued: Counter =
        Counter
            .builder(METRIC_ENQUEUED)
            .description("Events accepted into the ingestion queue")
            .register(meterRegistry)

    private val processed: Counter =
        Counter
            .builder(METRIC_PROCESSED)
            .description("Events durably written by the ingestion workers")
            .register(meterRegistry)

    private val failed: Counter =
        Counter
            .builder(METRIC_FAILED)
            .description("Events lost because their batch could not be persisted")
            .register(meterRegistry)

    private val dropped: Counter =
        Counter
            .builder(METRIC_DROPPED)
            .description("Events rejected because the ingestion queue was full")
            .register(meterRegistry)

    private val batchSize: DistributionSummary =
        DistributionSummary
            .builder(METRIC_BATCH_SIZE)
            .description("Number of events coalesced into one write")
            .register(meterRegistry)

    private val flushTimer: Timer =
        Timer
            .builder(METRIC_FLUSH)
            .description("Time spent persisting one batch of events")
            .register(meterRegistry)

    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(workerCount > 0) { "workerCount must be positive" }
        require(maxBatchSize > 0) { "maxBatchSize must be positive" }
        require(lingerMillis >= 0) { "lingerMillis must not be negative" }

        Gauge
            .builder(METRIC_BACKLOG, backlog) { it.get().toDouble() }
            .description("Events accepted but not yet written")
            .register(meterRegistry)

        workers =
            (1..workerCount).map {
                scope.launch { consume() }
            }
    }

    override fun enqueue(event: PendingEvent) {
        if (queue.trySend(event).isSuccess) {
            backlog.incrementAndGet()
            enqueued.increment()
            return
        }

        dropped.increment()
        val total = dropCounter.incrementAndGet()
        if (total == 1L || total % DROP_LOG_INTERVAL == 0L) {
            logger.warn {
                "Event queue is full ($queueCapacity slots); dropped $total event(s) so far. " +
                    "Consider raising application.ingestion.queue-capacity or the database pool size."
            }
        }
    }

    private suspend fun consume() {
        while (true) {
            val first = queue.receiveCatching().getOrNull() ?: return
            val batch = ArrayList<PendingEvent>(maxBatchSize)
            batch.add(first)

            drainInto(batch)
            flush(batch)
        }
    }

    /**
     * Fills [batch] up to [maxBatchSize], waiting at most [lingerMillis] for the next event.
     *
     * A worker that only takes what is already queued writes one row per transaction whenever
     * arrivals are slower than flushes, which is exactly the regime of a busy but healthy site.
     * Waiting briefly lets the batch fill and turns those commits into a few rows each; under a
     * real burst the wait is spent draining the queue, so nothing is added to the flush schedule.
     */
    private suspend fun drainInto(batch: MutableList<PendingEvent>) {
        if (lingerMillis <= 0) {
            while (batch.size < maxBatchSize) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch.add(next)
            }
            return
        }

        withTimeoutOrNull(lingerMillis) {
            while (batch.size < maxBatchSize) {
                val next = queue.receiveCatching().getOrNull() ?: return@withTimeoutOrNull
                batch.add(next)
            }
        }
    }

    private fun flush(batch: List<PendingEvent>) {
        batchSize.record(batch.size.toDouble())
        val sample = Timer.start()

        var written = 0
        var failure: Exception? = null
        try {
            written = persistence.persist(batch)
        } catch (error: Exception) {
            failure = error
        } finally {
            // Release the backlog *before* publishing the batch outcome below: an observer that
            // sees a batch counted as processed/failed must also see its events leave the backlog.
            backlog.addAndGet(-batch.size.toLong())
            sample.stop(flushTimer)
        }

        val failedWrite = failure
        if (failedWrite != null) {
            failed.increment(batch.size.toDouble())
            logger.error(failedWrite) { "Failed to persist a batch of ${batch.size} event(s)" }
        } else {
            processed.increment(written.toDouble())
            if (written < batch.size) {
                logger.debug { "Wrote $written of ${batch.size} batch event(s); the rest had no registered property" }
            }
        }
    }

    /** Stops accepting work, lets in-flight batches finish, then releases the workers. */
    fun shutdown() {
        queue.close()
        runBlocking {
            withTimeoutOrNull(SHUTDOWN_TIMEOUT_MS) { workers.joinAll() }
        }
        scope.cancel()
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 10_000
        const val DEFAULT_WORKER_COUNT = 8
        const val DEFAULT_MAX_BATCH_SIZE = 500

        /** How long a worker waits for its batch to fill before writing it. */
        const val DEFAULT_LINGER_MILLIS = 10L

        const val METRIC_ENQUEUED = "symbanta.ingestion.events.enqueued"
        const val METRIC_PROCESSED = "symbanta.ingestion.events.processed"
        const val METRIC_FAILED = "symbanta.ingestion.events.failed"
        const val METRIC_DROPPED = "symbanta.ingestion.events.dropped"
        const val METRIC_BACKLOG = "symbanta.ingestion.queue.backlog"
        const val METRIC_BATCH_SIZE = "symbanta.ingestion.batch.events"
        const val METRIC_FLUSH = "symbanta.ingestion.batch.flush"

        private const val DROP_LOG_INTERVAL = 1_000L
        private const val SHUTDOWN_TIMEOUT_MS = 5_000L
    }
}
