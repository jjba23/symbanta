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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.fixedClock
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel
import org.jointhefreeworld.symbanta.feature.console.port.inbound.ConsoleLogQuery
import org.jointhefreeworld.symbanta.feature.console.port.outbound.ConsoleLogQueryPort
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConsoleLogServiceSpec :
    FunSpec({

        val logs = mock<ConsoleLogQueryPort>()
        val service = ConsoleLogService(logs)

        beforeTest {
            org.mockito.kotlin.reset(logs)
        }

        test("normalises the domain and clamps the requested limit") {
            whenever(logs.recent(any(), any(), any())).thenReturn(emptyList())

            service.recent(
                ConsoleLogQuery(
                    domain = "  Example.COM ",
                    level = ConsoleLogLevel.ERROR,
                    limit = 10_000,
                ),
            )

            verify(logs).recent("example.com", ConsoleLogLevel.ERROR, ConsoleLogService.MAX_LIMIT)
        }

        test("keeps a limit inside the accepted range") {
            whenever(logs.recent(any(), any(), any())).thenReturn(emptyList())

            service.recent(ConsoleLogQuery(domain = "example.com", level = null, limit = 10))

            verify(logs).recent("example.com", null, 10)
        }

        test("maps entries one to one") {
            val entry =
                ConsoleLogEntry(
                    timestamp = fixedClock.instant(),
                    level = ConsoleLogLevel.WARN,
                    message = "deprecated API",
                    url = "https://example.com",
                    sessionId = "s1",
                    country = "NL",
                )
            whenever(logs.recent(any(), any(), any())).thenReturn(listOf(entry))

            service
                .recent(ConsoleLogQuery("example.com", ConsoleLogLevel.WARN, 10))
                .single() shouldBe entry
        }
    })
