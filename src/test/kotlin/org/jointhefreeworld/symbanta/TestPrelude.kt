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
package org.jointhefreeworld.symbanta

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TestPrelude {
    val amsterdamZone: ZoneId = ZoneId.of("Europe/Amsterdam")
    val fixedClock: Clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), amsterdamZone)

    fun amsterdamStartOfDayInstant(
        year: Int,
        month: Int,
        day: Int,
    ): Instant = LocalDate.of(year, month, day).atStartOfDay(amsterdamZone).toInstant()
}
