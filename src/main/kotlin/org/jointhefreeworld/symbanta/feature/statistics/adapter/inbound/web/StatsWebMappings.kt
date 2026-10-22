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
