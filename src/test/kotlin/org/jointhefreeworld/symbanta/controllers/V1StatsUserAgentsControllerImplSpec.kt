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

package org.jointhefreeworld.symbanta.controllers

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.TestPrelude.fixedClock
import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.domain.UserAgentStat
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class V1StatsUserAgentsControllerImplSpec :
    FunSpec({

        val repo = mock<AnalyticsRepository>()

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(V1StatsUserAgentsControllerImpl(repo, fixedClock))
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
            reset(repo)
        }

        context("GET /v1/stats/user-agents - Happy Path") {

            test("returns 200 OK with aggregated and sorted user agent statistics") {
                whenever(repo.getUserAgentStats(any(), any(), any())).thenReturn(
                    listOf(
                        UserAgentStat(
                            userAgent = @Suppress("ktlint:standard:max-line-length")
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                            pageViews = 100L,
                            visitors = 80L,
                        ),
                        UserAgentStat(
                            userAgent = @Suppress("ktlint:standard:max-line-length")
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                            pageViews = 50L,
                            visitors = 30L,
                        ),
                        UserAgentStat(
                            userAgent = @Suppress("ktlint:standard:max-line-length")
                            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1",
                            pageViews = 200L,
                            visitors = 150L,
                        ),
                    ),
                )

                performGetUserAgents().andExpect {
                    status { isOk() }
                    content { contentType(MediaType.APPLICATION_JSON) }
                    jsonPath("$.from") { value("2025-12-31T23:00:00Z") }
                    jsonPath("$.to") { value("2026-01-10T23:00:00Z") }
                    // Sorted descending by total aggregated page views (Safari first: 200, Chrome second: 150)
                    jsonPath("$.items[0].userAgent") { value("iOS / iPhone, Safari") }
                    jsonPath("$.items[0].pageViews") { value(200) }
                    jsonPath("$.items[0].visitors") { value(150) }
                    jsonPath("$.items[1].userAgent") { value("macOS, Chrome") }
                    jsonPath("$.items[1].pageViews") { value(100) }
                    jsonPath("$.items[1].visitors") { value(80) }
                }

                verify(repo).getUserAgentStats(
                    eq("example.com"),
                    eq(amsterdamStartOfDayInstant(2026, 1, 1)),
                    eq(amsterdamStartOfDayInstant(2026, 1, 11)),
                )
            }

            test("returns 200 OK with empty items list when repository returns no data") {
                whenever(repo.getUserAgentStats(any(), any(), any())).thenReturn(emptyList())

                performGetUserAgents(endDate = "2026-01-05").andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
            }

            test("returns 200 OK with empty items list for pre-defined period") {
                whenever(repo.getUserAgentStats(any(), any(), any())).thenReturn(emptyList())

                performGetUserAgents(
                    period = PeriodDto.LAST_24_HOURS.value,
                    startDate = null,
                    endDate = null,
                ).andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
            }
        }

        context("GET /v1/stats/user-agents - Unhappy Path") {

            context("validation errors return 400 Bad Request") {
                data class TestCase(
                    val name: String,
                    val domain: String?,
                    val period: String?,
                )

                withData(
                    TestCase("missing required domain parameter", domain = null, period = PeriodDto.CUSTOM.value),
                    TestCase("invalid period parameter value", domain = "example.com", period = "INVALID_PERIOD"),
                ) { testCase ->
                    performGetUserAgents(domain = testCase.domain, period = testCase.period).andExpect {
                        status { isBadRequest() }
                    }
                }
            }

            test("returns 500 Internal Server Error when repository throws an exception") {
                whenever(repo.getUserAgentStats(any(), any(), any()))
                    .thenThrow(RuntimeException("Database connection failed"))

                performGetUserAgents().andExpect {
                    status { isInternalServerError() }
                }
            }
        }
    })
