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

package org.jointhefreeworld.symbanta.adapter.inbound.web

import io.kotest.core.spec.style.FunSpec
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.application.port.inbound.BreakdownResult
import org.jointhefreeworld.symbanta.application.port.inbound.GetUserAgentStatsUseCase
import org.jointhefreeworld.symbanta.domain.model.UserAgentGroupStat
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class StatsUserAgentsControllerSpec :
    FunSpec({

        val userAgents = mock<GetUserAgentStatsUseCase>()

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(StatsUserAgentsController(userAgents))
                .setControllerAdvice(GlobalExceptionControllerAdvice::class.java)
                .build()

        fun performGetUserAgents(
            domain: String? = "example.com",
            period: String? = PeriodDto.CUSTOM.value,
            startDate: String? = "2026-01-01",
            endDate: String? = "2026-01-10",
        ): ResultActionsDsl =
            mockMvc.get("/v1/stats/user-agents") {
                domain?.let { param("domain", it) }
                period?.let { param("period", it) }
                startDate?.let { param("start-date", it) }
                endDate?.let { param("end-date", it) }
                accept = MediaType.APPLICATION_JSON
            }

        beforeTest {
            reset(userAgents)
        }

        context("GET /v1/stats/user-agents - Happy Path") {

            test("returns 200 OK with aggregated and sorted user agent statistics") {
                whenever(userAgents.userAgents(any())).thenReturn(
                    BreakdownResult(
                        from = amsterdamStartOfDayInstant(2026, 1, 1),
                        to = amsterdamStartOfDayInstant(2026, 1, 11),
                        items =
                            listOf(
                                UserAgentGroupStat(label = "iOS / iPhone, Safari", pageViews = 200L, visitors = 150L),
                                UserAgentGroupStat(label = "macOS, Chrome", pageViews = 100L, visitors = 80L),
                            ),
                    ),
                )

                performGetUserAgents().andExpect {
                    status { isOk() }
                    content { contentType(MediaType.APPLICATION_JSON) }
                    jsonPath("$.from") { value("2025-12-31T23:00:00Z") }
                    jsonPath("$.to") { value("2026-01-10T23:00:00Z") }
                    jsonPath("$.items[0].userAgent") { value("iOS / iPhone, Safari") }
                    jsonPath("$.items[0].pageViews") { value(200) }
                    jsonPath("$.items[0].visitors") { value(150) }
                    jsonPath("$.items[1].userAgent") { value("macOS, Chrome") }
                    jsonPath("$.items[1].pageViews") { value(100) }
                    jsonPath("$.items[1].visitors") { value(80) }
                }
            }

            test("returns 200 OK with empty items list when there is no data") {
                whenever(userAgents.userAgents(any())).thenReturn(
                    BreakdownResult(
                        from = amsterdamStartOfDayInstant(2026, 1, 1),
                        to = amsterdamStartOfDayInstant(2026, 1, 5),
                        items = emptyList(),
                    ),
                )

                performGetUserAgents(endDate = "2026-01-05").andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
            }
        }

        context("GET /v1/stats/user-agents - Unhappy Path") {

            test("returns 400 Bad Request when the domain parameter is missing") {
                performGetUserAgents(domain = null).andExpect {
                    status { isBadRequest() }
                }
            }

            test("returns 500 Internal Server Error when the use case fails unexpectedly") {
                whenever(userAgents.userAgents(any()))
                    .thenThrow(RuntimeException("Database connection failed"))

                performGetUserAgents().andExpect {
                    status { isInternalServerError() }
                }
            }
        }
    })
