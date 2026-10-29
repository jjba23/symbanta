/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.console.port.inbound

import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel

data class ConsoleLogQuery(
    val domain: String,
    val level: ConsoleLogLevel?,
    val limit: Int,
)

interface GetConsoleLogsUseCase {
    fun recent(query: ConsoleLogQuery): List<ConsoleLogEntry>
}
