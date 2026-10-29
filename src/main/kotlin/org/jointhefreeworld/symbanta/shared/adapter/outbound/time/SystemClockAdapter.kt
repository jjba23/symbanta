/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.adapter.outbound.time

import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import java.time.Instant

class SystemClockAdapter : ClockPort {
    override fun now(): Instant = Instant.now()
}
