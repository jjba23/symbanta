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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.application.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventDispatchPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.application.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.application.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.domain.model.AnalyticsEvent
import org.jointhefreeworld.symbanta.domain.model.EventId
import org.jointhefreeworld.symbanta.domain.model.EventType
import org.jointhefreeworld.symbanta.domain.model.EventTypeId
import org.jointhefreeworld.symbanta.domain.model.EventValidationException
import org.jointhefreeworld.symbanta.domain.model.Property
import org.jointhefreeworld.symbanta.domain.model.PropertyId
import org.jointhefreeworld.symbanta.domain.model.PropertyNotFoundException
import java.time.Instant
import java.util.UUID

class EventIngestionServiceSpec :
    FunSpec({

        val propertyId = PropertyId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val now = Instant.parse("2026-01-15T00:00:00Z")

        lateinit var properties: InMemoryPropertyRepository
        lateinit var eventTypes: InMemoryEventTypeRepository
        lateinit var eventStore: InMemoryEventStore
        lateinit var service: EventIngestionService

        beforeTest {
            properties =
                InMemoryPropertyRepository(
                    Property(propertyId, "example.com", now, null),
                )
            eventTypes = InMemoryEventTypeRepository()
            eventStore = InMemoryEventStore()
            service =
                EventIngestionService(
                    properties = properties,
                    eventTypes = eventTypes,
                    eventStore = eventStore,
                    dispatcher = ImmediateDispatcher(),
                    clock = FixedClockPort(now),
                )
        }

        fun command(
            name: String = "page-view",
            domain: String = "example.com",
            props: Map<String, Any?> = mapOf("plan" to "pro"),
            country: String? = "NL",
            headers: Map<String, String> = mapOf("user-agent" to "Mozilla/5.0"),
        ) = RegisterEventCommand(
            domain = domain,
            name = name,
            sessionId = "session-1",
            url = "https://example.com/home",
            referrer = "https://search.example",
            screenWidth = 1920,
            screenHeight = 1080,
            language = "en-US",
            userAgent = "Mozilla/5.0",
            props = props,
            country = country,
            headers = headers,
        )

        context("register") {
            test("stores the event with its request context, props and server timestamp") {
                val eventId = service.register(command())

                eventStore.events shouldHaveSize 1
                val stored = eventStore.events.single()

                stored.id shouldBe eventId
                stored.propertyId shouldBe propertyId
                stored.name shouldBe "page-view"
                stored.sessionId?.value shouldBe "session-1"
                stored.props shouldBe mapOf("plan" to "pro")
                stored.country shouldBe "NL"
                stored.headers shouldBe mapOf("user-agent" to "Mozilla/5.0")
                stored.createdAt shouldBe now
            }

            test("normalises the domain and trims the event name") {
                service.register(command(name = "  signup-click  ", domain = " Example.COM "))

                eventStore.events.single().name shouldBe "signup-click"
                eventStore.events.single().propertyId shouldBe propertyId
            }

            test("registers an event type once, even across repeated events") {
                service.register(command(name = "signup-click"))
                service.register(command(name = "signup-click"))
                service.register(command(name = "page-view"))

                eventTypes.types.map { it.name } shouldBe listOf("signup-click", "page-view")
                eventStore.events shouldHaveSize 3
            }

            test("fails fast on an invalid event name without storing anything") {
                shouldThrow<EventValidationException> {
                    service.register(command(name = "not valid!"))
                }

                eventStore.events.shouldBeEmpty()
                eventTypes.types.shouldBeEmpty()
            }

            test("rejects an unknown property domain") {
                shouldThrow<PropertyNotFoundException> {
                    service.register(command(domain = "unknown.org"))
                }

                eventStore.events.shouldBeEmpty()
            }
        }
    })

private class InMemoryPropertyRepository(
    private var property: Property?,
) : PropertyRepositoryPort {
    override fun all(): List<Property> = listOfNotNull(property)

    override fun byDomain(domain: String): Property? = property?.takeIf { it.domain == domain }

    override fun save(property: Property) {
        this.property = property
    }
}

private class InMemoryEventStore : EventStorePort {
    val events = mutableListOf<AnalyticsEvent>()

    override fun save(event: AnalyticsEvent): EventId {
        events += event
        return event.id
    }
}

private class InMemoryEventTypeRepository : EventTypeRepositoryPort {
    val types = mutableListOf<EventType>()

    override fun registerIfAbsent(
        propertyId: PropertyId,
        name: String,
        description: String?,
        now: Instant,
    ): EventType =
        types.firstOrNull { it.propertyId == propertyId && it.name == name }
            ?: EventType(EventTypeId(UUID.randomUUID()), propertyId, name, description, now)
                .also { types += it }

    override fun listForDomain(domain: String): List<EventType> = types
}

private class ImmediateDispatcher : EventDispatchPort {
    override fun dispatch(work: () -> Unit) = work()
}

private class FixedClockPort(
    private val instant: Instant,
) : ClockPort {
    override fun now(): Instant = instant
}
