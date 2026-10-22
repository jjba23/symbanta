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
