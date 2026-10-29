/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound

import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType

data class RegisterEventTypeCommand(
    val domain: String,
    val name: String,
    val description: String?,
)

interface RegisterEventTypeUseCase {
    fun register(command: RegisterEventTypeCommand): EventType
}

interface ListEventTypesUseCase {
    fun list(domain: String): List<EventType>
}
