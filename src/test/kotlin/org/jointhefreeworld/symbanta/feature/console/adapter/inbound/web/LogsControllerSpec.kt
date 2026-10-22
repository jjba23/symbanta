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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.fixedClock
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel
import org.jointhefreeworld.symbanta.feature.console.port.inbound.ConsoleLogQuery
import org.jointhefreeworld.symbanta.feature.console.port.inbound.GetConsoleLogsUseCase
import org.jointhefreeworld.symbanta.openapi.models.ConsoleLogLevelDto
import org.jointhefreeworld.symbanta.shared.adapter.inbound.web.GlobalExceptionControllerAdvice
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LogsControllerSpec :
    FunSpec({

        val logs = mock<GetConsoleLogsUseCase>()

        val conversionService =
            DefaultFormattingConversionService().apply {
                addConverter(String::class.java, ConsoleLogLevelDto::class.java) { source ->
                    ConsoleLogLevelDto.fromValue(source.trim().lowercase())
                        ?: throw IllegalArgumentException("Unknown console log level '$source'")
                }
            }

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(LogsController(logs))
                .setConversionService(conversionService)
                .setControllerAdvice(GlobalExceptionControllerAdvice::class.java)
                .build()

        beforeTest {
            reset(logs)
        }

        test("returns console entries with level, message and page context") {
            whenever(logs.recent(any())).thenReturn(
                listOf(
                    ConsoleLogEntry(
                        timestamp = fixedClock.instant(),
                        level = ConsoleLogLevel.ERROR,
                        message = "Failed to load /api/user: 500",
                        url = "https://example.com/dashboard",
                        sessionId = "session-1",
                        country = "NL",
                    ),
                ),
            )

            mockMvc
                .get("/v1/logs") {
                    param("domain", "example.com")
                    param("limit", "5")
                    param("level", "error")
                    accept = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.items[0].level") { value("error") }
                    jsonPath("$.items[0].message") { value("Failed to load /api/user: 500") }
                    jsonPath("$.items[0].url") { value("https://example.com/dashboard") }
                    jsonPath("$.items[0].country") { value("NL") }
                }

            val captor = argumentCaptor<ConsoleLogQuery>()
            verify(logs).recent(captor.capture())
            captor.firstValue shouldBe ConsoleLogQuery("example.com", ConsoleLogLevel.ERROR, 5)
        }

        test("returns an empty list when nothing was captured") {
            whenever(logs.recent(any())).thenReturn(emptyList())

            mockMvc
                .get("/v1/logs") {
                    param("domain", "example.com")
                    accept = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
        }

        test("returns 400 Bad Request when the limit is out of range") {
            mockMvc
                .get("/v1/logs") {
                    param("domain", "example.com")
                    param("limit", "100000")
                    accept = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isBadRequest() }
                }
        }
    })
