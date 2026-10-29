/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.domain

import org.jointhefreeworld.symbanta.shared.domain.EventId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.jointhefreeworld.symbanta.shared.domain.SessionId
import java.time.Instant

/**
 * Well-known event names. Symbanta accepts any free-form name, these are the ones the
 * JavaScript telemetry client emits out of the box.
 */
object EventNames {
    const val PAGE_VIEW = "page-view"
    const val HEARTBEAT = "heartbeat"
    const val VISIBILITY_CHANGE = "visibility-change"

    /** Console output forwarded by the telemetry client (level/message live in [AnalyticsEvent.props]). */
    const val CONSOLE = "console"

    /** Maximum accepted event name length, guards against accidental payload abuse. */
    const val MAX_LENGTH = 120

    fun isValid(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= MAX_LENGTH &&
            name.all { it.isLetterOrDigit() || it in "-_.:" }
}

/**
 * A recorded event. Deliberately free of persistence or HTTP concerns.
 *
 * [headers] only ever contains an allow-list of request headers: no IP address, cookie,
 * authorization or other identifying header is stored.
 */
data class AnalyticsEvent(
    val id: EventId,
    val propertyId: PropertyId,
    val sessionId: SessionId?,
    val name: String,
    val url: String?,
    val referrer: String?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val language: String?,
    val userAgent: String?,
    val props: Map<String, Any?>,
    val country: String?,
    val headers: Map<String, String>,
    val createdAt: Instant,
)
