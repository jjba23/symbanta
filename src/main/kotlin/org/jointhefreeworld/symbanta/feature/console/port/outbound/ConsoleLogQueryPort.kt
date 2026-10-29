/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.console.port.outbound

import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogEntry
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel

/**
 * Driven port: reads captured `console.*` entries (#6) for one property.
 */
interface ConsoleLogQueryPort {
    /** Newest first. A null [level] returns every level. */
    fun recent(
        domain: String,
        level: ConsoleLogLevel?,
        limit: Int,
    ): List<ConsoleLogEntry>
}
