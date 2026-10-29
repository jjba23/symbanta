/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.StatsQuery
import org.jointhefreeworld.symbanta.openapi.models.PeriodDto
import org.jointhefreeworld.symbanta.shared.domain.PeriodSelection
import org.jointhefreeworld.symbanta.shared.domain.TimePeriod
import java.time.LocalDate

/**
 * Translation between transport DTOs (generated from the OpenAPI contract) and domain types.
 * Keeping it in the inbound adapter means neither the domain nor the application layer knows
 * about generated classes.
 */
internal fun PeriodDto.toDomainPeriod(): TimePeriod = TimePeriod.valueOf(name)

internal fun TimePeriod.toDtoPeriod(): PeriodDto = PeriodDto.valueOf(name)

internal fun statsQuery(
    domain: String,
    period: PeriodDto,
    startDate: LocalDate?,
    endDate: LocalDate?,
): StatsQuery =
    StatsQuery(
        domain = domain,
        period = PeriodSelection(period = period.toDomainPeriod(), startDate = startDate, endDate = endDate),
    )
