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

package org.jointhefreeworld.symbanta.feature.console.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel
import org.jointhefreeworld.symbanta.feature.console.port.inbound.ConsoleLogQuery
import org.jointhefreeworld.symbanta.feature.console.port.inbound.GetConsoleLogsUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1LogsController
import org.jointhefreeworld.symbanta.openapi.models.ConsoleLogLevelDto
import org.jointhefreeworld.symbanta.openapi.models.V1ConsoleLogEntryDto
import org.jointhefreeworld.symbanta.openapi.models.V1ConsoleLogsResponseDto
import org.jointhefreeworld.symbanta.shared.domain.EventValidationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class LogsController(
    private val logs: GetConsoleLogsUseCase,
) : V1LogsController {
    override fun getConsoleLogs(
        domain: String,
        limit: Int,
        level: ConsoleLogLevelDto?,
    ): ResponseEntity<V1ConsoleLogsResponseDto> {
        if (limit !in 1..MAX_LIMIT) {
            throw EventValidationException("limit must be between 1 and $MAX_LIMIT")
        }

        val entries =
            logs.recent(
                ConsoleLogQuery(
                    domain = domain,
                    level = level.toDomainLevel(),
                    limit = limit,
                ),
            )

        return ResponseEntity.ok(
            V1ConsoleLogsResponseDto(
                items =
                    entries.map { entry ->
                        V1ConsoleLogEntryDto(
                            timestamp = entry.timestamp,
                            level = entry.level.toDtoLevel(),
                            message = entry.message,
                            url = entry.url?.let { runCatching { URI(it) }.getOrNull() },
                            sessionId = entry.sessionId,
                            country = entry.country,
                        )
                    },
            ),
        )
    }

    private fun ConsoleLogLevelDto?.toDomainLevel(): ConsoleLogLevel? = this?.let { ConsoleLogLevel.fromWire(it.value) }

    private fun ConsoleLogLevel.toDtoLevel(): ConsoleLogLevelDto = ConsoleLogLevelDto.fromValue(wireValue) ?: ConsoleLogLevelDto.LOG

    private companion object {
        const val MAX_LIMIT = 1000
    }
}
