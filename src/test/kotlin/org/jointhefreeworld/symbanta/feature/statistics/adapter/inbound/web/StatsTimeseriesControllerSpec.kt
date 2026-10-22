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

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.amsterdamStartOfDayInstant
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeGranularity
import org.jointhefreeworld.symbanta.feature.statistics.domain.TimeSeriesPoint
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetTimeSeriesUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.TimeSeriesResult
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.shared.adapter.inbound.web.GlobalExceptionControllerAdvice
import org.jointhefreeworld.symbanta.shared.domain.InvalidPeriodException
import org.jointhefreeworld.symbanta.shared.domain.PeriodSelection
import org.jointhefreeworld.symbanta.shared.domain.TimePeriod
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
import java.time.Instant
import java.time.LocalDate

class StatsTimeseriesControllerSpec :
    FunSpec({

        val timeSeries = mock<GetTimeSeriesUseCase>()

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(StatsTimeseriesController(timeSeries))
                .setControllerAdvice(GlobalExceptionControllerAdvice::class.java)
                .build()

        fun performGetTimeseries(
            domain: String? = "example.com",
            period: String? = PeriodDto.CUSTOM.value,
            startDate: String? = "2026-01-01",
            endDate: String? = "2026-01-10",
        ): ResultActionsDsl =
            mockMvc.get("/v1/stats/timeseries") {
                domain?.let { param("domain", it) }
                period?.let { param("period", it) }
                startDate?.let { param("start-date", it) }
                endDate?.let { param("end-date", it) }
                accept = MediaType.APPLICATION_JSON
            }

        beforeTest {
            reset(timeSeries)
        }

        context("GET /v1/stats/timeseries - Happy Path") {

            test("returns 200 OK with the granularity, window and points") {
                whenever(timeSeries.timeSeries(any())).thenReturn(
                    TimeSeriesResult(
                        granularity = TimeGranularity.DAY,
                        from = amsterdamStartOfDayInstant(2026, 1, 1),
                        to = amsterdamStartOfDayInstant(2026, 1, 11),
                        points =
                            listOf(
                                TimeSeriesPoint(
                                    timestamp = amsterdamStartOfDayInstant(2026, 1, 9),
                                    pageViews = 12,
                                    visitors = 5,
                                ),
                                TimeSeriesPoint(
                                    timestamp = amsterdamStartOfDayInstant(2026, 1, 10),
                                    pageViews = 0,
                                    visitors = 0,
                                ),
                            ),
                    ),
                )

                performGetTimeseries().andExpect {
                    status { isOk() }
                    content { contentType(MediaType.APPLICATION_JSON) }
                    jsonPath("$.granularity") { value("DAY") }
                    jsonPath("$.from") { value("2025-12-31T23:00:00Z") }
                    jsonPath("$.to") { value("2026-01-10T23:00:00Z") }
                    jsonPath("$.points[0].timestamp") { value("2026-01-08T23:00:00Z") }
                    jsonPath("$.points[0].pageViews") { value(12) }
                    jsonPath("$.points[0].visitors") { value(5) }
                    jsonPath("$.points[1].pageViews") { value(0) }
                }

                val captor = argumentCaptor<StatsQuery>()
                verify(timeSeries).timeSeries(captor.capture())
                captor.firstValue.domain shouldBe "example.com"
                captor.firstValue.period shouldBe
                    PeriodSelection(
                        period = TimePeriod.CUSTOM,
                        startDate = LocalDate.of(2026, 1, 1),
                        endDate = LocalDate.of(2026, 1, 10),
                    )
            }

            test("maps hourly granularity") {
                whenever(timeSeries.timeSeries(any())).thenReturn(
                    TimeSeriesResult(
                        granularity = TimeGranularity.HOUR,
                        from = Instant.parse("2026-01-14T00:00:00Z"),
                        to = Instant.parse("2026-01-15T00:00:00Z"),
                        points =
                            listOf(
                                TimeSeriesPoint(
                                    timestamp = Instant.parse("2026-01-14T00:00:00Z"),
                                    pageViews = 3,
                                    visitors = 1,
                                ),
                            ),
                    ),
                )

                performGetTimeseries(
                    period = PeriodDto.LAST_24_HOURS.value,
                    startDate = null,
                    endDate = null,
                ).andExpect {
                    status { isOk() }
                    jsonPath("$.granularity") { value("HOUR") }
                    jsonPath("$.points[0].timestamp") { value("2026-01-14T00:00:00Z") }
                }
            }

            test("returns 200 OK with an empty points list when there is no traffic") {
                whenever(timeSeries.timeSeries(any())).thenReturn(
                    TimeSeriesResult(
                        granularity = TimeGranularity.DAY,
                        from = amsterdamStartOfDayInstant(2026, 1, 1),
                        to = amsterdamStartOfDayInstant(2026, 1, 2),
                        points = emptyList(),
                    ),
                )

                performGetTimeseries(endDate = "2026-01-01").andExpect {
                    status { isOk() }
                    jsonPath("$.points") { isEmpty() }
                }
            }
        }

        context("GET /v1/stats/timeseries - Unhappy Path") {

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
                    performGetTimeseries(domain = testCase.domain, period = testCase.period).andExpect {
                        status { isBadRequest() }
                    }
                }
            }

            test("returns 400 Bad Request when the use case rejects the period") {
                whenever(timeSeries.timeSeries(any())).thenThrow(InvalidPeriodException("bad range"))

                performGetTimeseries().andExpect {
                    status { isBadRequest() }
                }
            }

            test("returns 500 Internal Server Error when the use case fails unexpectedly") {
                whenever(timeSeries.timeSeries(any())).thenThrow(RuntimeException("Database connection failed"))

                performGetTimeseries().andExpect {
                    status { isInternalServerError() }
                }
            }
        }
    })
