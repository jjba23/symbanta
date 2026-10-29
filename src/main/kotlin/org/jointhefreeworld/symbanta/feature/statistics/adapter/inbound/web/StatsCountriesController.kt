/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetCountryStatsUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1StatsCountriesController
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.openapi.models.V1CountryStatDto
import org.jointhefreeworld.symbanta.openapi.models.V1CountryStatsResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class StatsCountriesController(
    private val countries: GetCountryStatsUseCase,
) : V1StatsCountriesController {
    override fun getCountryStats(
        domain: String,
        period: PeriodDto,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ResponseEntity<V1CountryStatsResponseDto> {
        val result = countries.countries(statsQuery(domain, period, startDate, endDate))

        return ResponseEntity.ok(
            V1CountryStatsResponseDto(
                from = result.from,
                to = result.to,
                items =
                    result.items.map {
                        V1CountryStatDto(
                            country = it.country,
                            pageViews = it.pageViews.toInt(),
                            visitors = it.visitors.toInt(),
                        )
                    },
            ),
        )
    }
}
