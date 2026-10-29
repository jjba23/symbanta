/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.port.outbound

import java.time.Instant

/** Driven port: time, so use cases stay deterministic under test. */
interface ClockPort {
    fun now(): Instant
}
