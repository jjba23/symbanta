/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.console.domain

import java.time.Instant

enum class ConsoleLogLevel(
    val wireValue: String,
) {
    LOG("log"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    DEBUG("debug"),
    ;

    companion object {
        fun fromWire(value: String?): ConsoleLogLevel? = entries.firstOrNull { it.wireValue.equals(value?.lowercase(), ignoreCase = true) }
    }
}

/**
 * A console message forwarded by an instrumented application.
 */
data class ConsoleLogEntry(
    val timestamp: Instant,
    val level: ConsoleLogLevel,
    val message: String,
    val url: String?,
    val sessionId: String?,
    val country: String?,
)
