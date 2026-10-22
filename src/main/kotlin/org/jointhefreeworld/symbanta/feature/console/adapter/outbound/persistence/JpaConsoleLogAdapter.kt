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
