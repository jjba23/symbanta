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

package org.jointhefreeworld.symbanta.application.service

import org.jointhefreeworld.symbanta.application.port.inbound.ListEventTypesUseCase
import org.jointhefreeworld.symbanta.application.port.inbound.RegisterEventTypeCommand
import org.jointhefreeworld.symbanta.application.port.inbound.RegisterEventTypeUseCase
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.application.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.domain.model.EventNames
import org.jointhefreeworld.symbanta.domain.model.EventType
import org.jointhefreeworld.symbanta.domain.model.EventValidationException
import org.jointhefreeworld.symbanta.domain.model.PropertyNotFoundException

/**
 * Registers and lists the event names an application may emit (#4).
 */
class EventTypeService(
    private val properties: PropertyRepositoryPort,
    private val eventTypes: EventTypeRepositoryPort,
    private val clock: ClockPort,
) : RegisterEventTypeUseCase,
    ListEventTypesUseCase {
    override fun register(command: RegisterEventTypeCommand): EventType {
        val name = command.name.trim()
        if (!EventNames.isValid(name)) {
            throw EventValidationException(
                "Invalid event name '$name': use 1-${EventNames.MAX_LENGTH} letters, digits or -_.:",
            )
        }

        val domain = command.domain.trim().lowercase()
        val property =
            properties.byDomain(domain)
                ?: throw PropertyNotFoundException(command.domain)

        return eventTypes.registerIfAbsent(
            propertyId = property.id,
            name = name,
            description = command.description?.trim()?.takeIf { it.isNotEmpty() },
            now = clock.now(),
        )
    }

    override fun list(domain: String): List<EventType> = eventTypes.listForDomain(domain.trim().lowercase())
}
