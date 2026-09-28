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
import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.domain.RouteStat
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.*

class V1StatsRoutesControllerImplSpec :
    FunSpec({

        val stubRepository = mock<AnalyticsRepository>()
        val fixedClock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC)
        val controller = V1StatsRoutesControllerImpl(stubRepository, fixedClock)

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(GlobalExceptionControllerAdvice::class.java)
                .build()

        beforeTest {
            reset(stubRepository)
        }

        context("GET /v1/stats/routes - Happy Path") {

            test("returns 200 OK with formatted route statistics") {
                whenever(stubRepository.getRouteStats(any(), any(), any())).thenReturn(
                    listOf(
                        RouteStat(route = "/home", pageViews = 150L, visitors = 100L),
                        RouteStat(route = "/about", pageViews = 50L, visitors = 30L),
                    ),
                )

                mockMvc
                    .get("/v1/stats/routes") {
                        param("domain", "example.com")
                        param("period", PeriodDto.CUSTOM.value)
                        param("start-date", "2026-01-01")
                        param("end-date", "2026-01-10")
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
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

                verify(stubRepository, times(1)).getRouteStats(
                    eq("example.com"),
                    eq(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.of("Europe/Amsterdam")).toInstant()),
                    eq(LocalDate.of(2026, 1, 11).atStartOfDay(ZoneId.of("Europe/Amsterdam")).toInstant()),
                )
            }

            test("returns 200 OK with empty items list when repository returns no data") {
                whenever(stubRepository.getRouteStats(any(), any(), any())).thenReturn(emptyList())

                mockMvc
                    .get("/v1/stats/routes") {
                        param("domain", "example.com")
                        param("period", PeriodDto.CUSTOM.value)
                        param("start-date", "2026-01-01")
                        param("end-date", "2026-01-05")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.items") { isEmpty() }
                    }
            }
            test("returns 200 OK with empty items list for pre-defined period when repository returns no data") {
                whenever(stubRepository.getRouteStats(any(), any(), any())).thenReturn(emptyList())

                mockMvc
                    .get("/v1/stats/routes") {
                        param("domain", "example.com")
                        param("period", PeriodDto.LAST_24_HOURS.value)
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.items") { isEmpty() }
                    }
            }
        }

        context("GET /v1/stats/routes - Unhappy Path") {

            test("returns 400 Bad Request when required 'domain' query parameter is missing") {
                mockMvc
                    .get("/v1/stats/routes") {
                        param("period", PeriodDto.CUSTOM.value)
                        param("start-date", "2026-01-01")
                        param("end-date", "2026-01-10")
                    }.andExpect {
                        status { isBadRequest() }
                    }
            }

            test("returns 400 Bad Request when 'period' parameter value is invalid") {
                mockMvc
                    .get("/v1/stats/routes") {
                        param("domain", "example.com")
                        param("period", "INVALID_PERIOD")
                    }.andExpect {
                        status { isBadRequest() }
                    }
            }

            test("returns 500 Internal Server Error when repository throws an unhandled exception") {
                whenever(stubRepository.getRouteStats(any(), any(), any()))
                    .thenThrow(RuntimeException("Database connection failed"))

                mockMvc
                    .get("/v1/stats/routes") {
                        param("domain", "example.com")
                        param("period", PeriodDto.CUSTOM.value)
                        param("start-date", "2026-01-01")
                        param("end-date", "2026-01-10")
                    }.andExpect {
                        status { isInternalServerError() }
                    }
            }
        }
    })
