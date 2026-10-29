/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
