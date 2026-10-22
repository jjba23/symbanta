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

package org.jointhefreeworld.symbanta.adapter.outbound.queue

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jointhefreeworld.symbanta.application.port.outbound.EventDispatchPort
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
 * - a drop-with-warning policy once the queue is full: the API keeps answering 202 and the
 *   dropped count is logged (rate limited) instead of growing the heap without bound.
 */
class CoroutineEventDispatcher(
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
    workerCount: Int = DEFAULT_WORKER_COUNT,
) : EventDispatchPort {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<() -> Unit>(queueCapacity)
    private val processed = AtomicLong()
    private val dropped = AtomicLong()

    init {
        require(queueCapacity > 0) { "queueCapacity must be positive" }
        require(workerCount > 0) { "workerCount must be positive" }

        repeat(workerCount) {
            scope.launch {
                for (work in queue) {
                    runCatching(work).onFailure { error ->
                        logger.error(error) { "Background event processing failed" }
                    }
                    processed.incrementAndGet()
                }
            }
        }
    }

    override fun dispatch(work: () -> Unit) {
        if (queue.trySend(work).isSuccess) {
            return
        }

        val total = dropped.incrementAndGet()
        if (total == 1L || total % DROP_LOG_INTERVAL == 0L) {
            logger.warn {
                "Event queue is full ($queueCapacity slots); dropped $total event(s) so far. " +
                    "Consider raising application.ingestion.queue-capacity or the database pool size."
            }
        }
    }

    /** Number of events written by the workers (useful for health/metrics checks). */
    fun processedCount(): Long = processed.get()

    /** Number of events rejected because the queue was full. */
    fun droppedCount(): Long = dropped.get()

    /** Stops accepting work and cancels in-flight coroutines on application shutdown. */
    fun shutdown() {
        queue.close()
        scope.cancel()
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 10_000
        const val DEFAULT_WORKER_COUNT = 8
        private const val DROP_LOG_INTERVAL = 1_000L
    }
}
