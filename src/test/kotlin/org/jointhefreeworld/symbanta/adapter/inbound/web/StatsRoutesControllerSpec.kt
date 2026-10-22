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
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.application.port.inbound.BreakdownResult
import org.jointhefreeworld.symbanta.application.port.inbound.GetRouteStatsUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.domain.model.PeriodSelection
import org.jointhefreeworld.symbanta.domain.model.RouteStat
import org.jointhefreeworld.symbanta.domain.model.TimePeriod
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class StatsRoutesControllerSpec :
    FunSpec({

        val routes = mock<GetRouteStatsUseCase>()

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(StatsRoutesController(routes))
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
            reset(routes)
        }

        context("GET /v1/stats/routes - Happy Path") {

            test("returns 200 OK with formatted route statistics") {
                whenever(routes.routes(any())).thenReturn(
                    BreakdownResult(
                        from = amsterdamStartOfDayInstant(2026, 1, 1),
                        to = amsterdamStartOfDayInstant(2026, 1, 11),
                        items =
                            listOf(
                                RouteStat(route = "/home", pageViews = 150L, visitors = 100L),
                                RouteStat(route = "/about", pageViews = 50L, visitors = 30L),
                            ),
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

                val captor = argumentCaptor<StatsQuery>()
                verify(routes).routes(captor.capture())
                captor.firstValue.domain shouldBe "example.com"
                captor.firstValue.period shouldBe
                    PeriodSelection(
                        period = TimePeriod.CUSTOM,
                        startDate = LocalDate.of(2026, 1, 1),
                        endDate = LocalDate.of(2026, 1, 10),
                    )
            }

            test("returns 200 OK with empty items list when there is no data") {
                whenever(routes.routes(any())).thenReturn(
                    BreakdownResult(
                        from = amsterdamStartOfDayInstant(2026, 1, 1),
                        to = amsterdamStartOfDayInstant(2026, 1, 5),
                        items = emptyList(),
                    ),
                )

                performGetRoutes(endDate = "2026-01-05").andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }
            }

            test("maps pre-defined periods to the domain period") {
                whenever(routes.routes(any())).thenReturn(
                    BreakdownResult(
                        from = amsterdamStartOfDayInstant(2026, 1, 14),
                        to = amsterdamStartOfDayInstant(2026, 1, 15),
                        items = emptyList(),
                    ),
                )

                performGetRoutes(
                    period = PeriodDto.LAST_24_HOURS.value,
                    startDate = null,
                    endDate = null,
                ).andExpect {
                    status { isOk() }
                    jsonPath("$.items") { isEmpty() }
                }

                val captor = argumentCaptor<StatsQuery>()
                verify(routes).routes(captor.capture())
                captor.firstValue.period.period shouldBe TimePeriod.LAST_24_HOURS
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

            test("returns 400 Bad Request when the use case rejects the period") {
                whenever(routes.routes(any()))
                    .thenThrow(
                        org.jointhefreeworld.symbanta.domain.model
                            .InvalidPeriodException("bad range"),
                    )

                performGetRoutes().andExpect {
                    status { isBadRequest() }
                }
            }

            test("returns 500 Internal Server Error when the use case fails unexpectedly") {
                whenever(routes.routes(any()))
                    .thenThrow(RuntimeException("Database connection failed"))

                performGetRoutes().andExpect {
                    status { isInternalServerError() }
                }
            }
        }
    })
