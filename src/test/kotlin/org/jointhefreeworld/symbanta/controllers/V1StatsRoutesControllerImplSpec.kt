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
import org.jointhefreeworld.symbanta.domain.RouteStat
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class V1StatsRoutesControllerImplSpec :
    FunSpec({

        val repo = mock<AnalyticsRepository>()

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(V1StatsRoutesControllerImpl(repo, fixedClock))
                .setControllerAdvice(GlobalExceptionControllerAdvice::class.java)
                .build()

        fun performGetRoutes(
            domain: String? = "example.com",
            period: String? = PeriodDto.CUSTOM.value,
            startDate: String? = "2026-01-01",
            endDate: String? = "2026-01-10",
        ): ResultActionsDsl =
            mockMvc.get("/v1/stats/routes") {
                domain?.let { param("domain", it) }
                period?.let { param("period", it) }
                startDate?.let { param("start-date", it) }
                endDate?.let { param("end-date", it) }
                accept = MediaType.APPLICATION_JSON
            }

        beforeTest {
            reset(repo)
        }

        context("GET /v1/stats/routes - Happy Path") {

            test("returns 200 OK with formatted route statistics") {
                whenever(repo.getRouteStats(any(), any(), any())).thenReturn(
                    listOf(
                        RouteStat(route = "/home", pageViews = 150L, visitors = 100L),
                        RouteStat(route = "/about", pageViews = 50L, visitors = 30L),
                    ),
                )

                performGetRoutes().andExpect {
                    status { isOk() }
                    content { contentType(MediaType.APPLICATION_JSON) }
                    jsonPath("$.from") { value("2025-12-31T23:00:00Z") }
                    jsonPath("$.to") { value("2026-01-10T23:00:00Z") }
                    jsonPath("$.items[0].route") { value("/home") }
                    jsonPath("$.items[0].pageViews") { value(150) }
                    jsonPath("$.items[0].visitors") { value(100) }
                    jsonPath("$.items[1].route") { value("/about") }
                    jsonPath("$.items[1].pageViews") { value(50) }
                    jsonPath("$.items[1].visitors") { value(30) }
                }

                verify(repo).getRouteStats(
                    eq("example.com"),
                    eq(amsterdamStartOfDayInstant(2026, 1, 1)),
                    eq(amsterdamStartOfDayInstant(2026, 1, 11)),
                )
            }

            test("returns 200 OK with empty items list when repository returns no data") {
                whenever(repo.getRouteStats(any(), any(), any())).thenReturn(emptyList())

                performGetRoutes(endDate = "2026-01-05").andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
            }

            test("returns 200 OK with empty items list for pre-defined period") {
                whenever(repo.getRouteStats(any(), any(), any())).thenReturn(emptyList())

                performGetRoutes(
                    period = PeriodDto.LAST_24_HOURS.value,
                    startDate = null,
                    endDate = null,
                ).andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
            }
        }

        context("GET /v1/stats/routes - Unhappy Path") {

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
                    performGetRoutes(domain = testCase.domain, period = testCase.period).andExpect {
                        status { isBadRequest() }
                    }
                }
            }

            test("returns 500 Internal Server Error when repository throws an exception") {
                whenever(repo.getRouteStats(any(), any(), any()))
                    .thenThrow(RuntimeException("Database connection failed"))

                performGetRoutes().andExpect {
                    status { isInternalServerError() }
                }
            }
        }
    })
