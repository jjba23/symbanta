/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.console.service

import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.port.inbound.ConsoleLogQuery
import org.jointhefreeworld.symbanta.feature.console.port.inbound.GetConsoleLogsUseCase
import org.jointhefreeworld.symbanta.feature.console.port.outbound.ConsoleLogQueryPort

/**
 * Reads the console output forwarded by instrumented applications (#6).
 */
class ConsoleLogService(
    private val logs: ConsoleLogQueryPort,
) : GetConsoleLogsUseCase {
    override fun recent(query: ConsoleLogQuery): List<ConsoleLogEntry> =
        logs.recent(
            domain = query.domain.trim().lowercase(),
            level = query.level,
            limit = query.limit.coerceIn(1, MAX_LIMIT),
        )

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1000
    }
}
