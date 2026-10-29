/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.shared.domain

import java.util.UUID

@JvmInline
value class PropertyId(
    val value: UUID,
)

@JvmInline
value class EventId(
    val value: UUID,
)

@JvmInline
value class EventTypeId(
    val value: UUID,
)

@JvmInline
value class SessionId(
    val value: String,
)
