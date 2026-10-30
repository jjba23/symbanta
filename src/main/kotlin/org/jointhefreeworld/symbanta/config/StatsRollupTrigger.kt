/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup.StatsRollupRefresher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Keeps the analytics rollups in step with ingestion.
 *
 * The interval is the freshness of the dashboard's current hour: a read trusts the rollups up to
 * the last completed run and aggregates only what came in after it from the event log.
 */
@Component
class StatsRollupTrigger(
    private val refresher: StatsRollupRefresher,
    private val config: StatisticsRollupConfig,
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(
        fixedDelayString = "\${application.stats.rollup.refresh-interval:15s}",
        initialDelayString = "10s",
    )
    fun refresh() {
        if (!config.enabled) return

        // A failed run leaves the coverage watermark untouched, so the next tick retries the same
        // range; the dashboard keeps answering from the event log for the part it lost.
        runCatching { refresher.refresh() }
            .onFailure { error -> logger.error(error) { "Could not refresh the analytics rollups" } }
    }
}
