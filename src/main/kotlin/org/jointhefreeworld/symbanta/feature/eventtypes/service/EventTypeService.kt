/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.service

import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.ListEventTypesUseCase
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.RegisterEventTypeCommand
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.RegisterEventTypeUseCase
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.EventValidationException
import org.jointhefreeworld.symbanta.shared.domain.PropertyNotFoundException
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort

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
