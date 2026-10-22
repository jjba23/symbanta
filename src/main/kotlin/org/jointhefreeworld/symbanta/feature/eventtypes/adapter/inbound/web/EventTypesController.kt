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

package org.jointhefreeworld.symbanta.feature.eventtypes.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.ListEventTypesUseCase
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.RegisterEventTypeCommand
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.RegisterEventTypeUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1EventTypesController
import org.jointhefreeworld.symbanta.openapi.models.V1EventTypeDto
import org.jointhefreeworld.symbanta.openapi.models.V1EventTypeRegistrationRequestDto
import org.jointhefreeworld.symbanta.openapi.models.V1EventTypesResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/** HTTP adapter for the per-application event type registry (#4). */
@RestController
class EventTypesController(
    private val registerEventType: RegisterEventTypeUseCase,
    private val listEventTypes: ListEventTypesUseCase,
) : V1EventTypesController {
    override fun getEventTypes(domain: String): ResponseEntity<V1EventTypesResponseDto> =
        ResponseEntity.ok(
            V1EventTypesResponseDto(
                eventTypes = listEventTypes.list(domain).map(::toDto),
            ),
        )

    override fun registerEventType(v1EventTypeRegistrationRequest: V1EventTypeRegistrationRequestDto): ResponseEntity<V1EventTypeDto> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                toDto(
                    registerEventType.register(
                        RegisterEventTypeCommand(
                            domain = v1EventTypeRegistrationRequest.domain,
                            name = v1EventTypeRegistrationRequest.name,
                            description = v1EventTypeRegistrationRequest.description,
                        ),
                    ),
                ),
            )

    private fun toDto(type: EventType): V1EventTypeDto =
        V1EventTypeDto(
            id = type.id.value,
            name = type.name,
            description = type.description,
            createdAt = type.createdAt,
        )
}
