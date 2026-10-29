/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.domain

import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import java.time.Instant

/**
 * A property is a registered application (usually a domain) whose telemetry Symbanta accepts.
 */
data class Property(
    val id: PropertyId,
    val domain: String,
    val createdAt: Instant,
    val updatedAt: Instant?,
)
