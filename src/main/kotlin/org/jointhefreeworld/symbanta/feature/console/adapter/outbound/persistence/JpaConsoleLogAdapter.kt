/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.console.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel
import org.jointhefreeworld.symbanta.feature.console.port.outbound.ConsoleLogQueryPort
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.EventEntity
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.EventJpaRepository
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

/**
 * Reads the `console` events captured by the telemetry client (#6) through Spring Data JPA.
 *
 * The level lives inside the `props` JSON document, so it cannot be filtered in JPQL. The adapter
 * scans the newest [SCAN_LIMIT] console events of the property and filters them in memory; console
 * capture is opt-in and low volume, which keeps that scan bounded.
 */
open class JpaConsoleLogAdapter(
    private val events: EventJpaRepository,
) : ConsoleLogQueryPort {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun recent(
        domain: String,
        level: ConsoleLogLevel?,
        limit: Int,
    ): List<ConsoleLogEntry> {
        logger.debug { "Fetching recent console logs from database" }

        return events
            .findConsoleEvents(domain, EventNames.CONSOLE, PageRequest.of(0, SCAN_LIMIT))
            .asSequence()
            .map { it.toConsoleLog() }
            .filter { level == null || it.level == level }
            .take(limit)
            .toList()
    }

    private companion object {
        const val SCAN_LIMIT = 10_000
    }
}

private fun EventEntity.toConsoleLog(): ConsoleLogEntry =
    ConsoleLogEntry(
        timestamp = createdAt,
        level = ConsoleLogLevel.fromWire(props["level"]?.toString()) ?: ConsoleLogLevel.LOG,
        message = props["message"]?.toString() ?: "",
        url = url,
        sessionId = sessionId,
        country = country,
    )
