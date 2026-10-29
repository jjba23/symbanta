/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
