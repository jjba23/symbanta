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

package org.jointhefreeworld.symbanta.feature.events.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.EventId
import org.jointhefreeworld.symbanta.shared.domain.EventTypeId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import java.time.Instant
import java.util.UUID

class EventPersistenceServiceSpec :
    FunSpec({

        val now = Instant.parse("2026-01-15T00:00:00Z")
        val property = Property(PropertyId(UUID.randomUUID()), "example.com", now, null)

        lateinit var properties: InMemoryProperties
        lateinit var eventTypes: InMemoryEventTypes
        lateinit var store: RecordingStore
        lateinit var service: EventPersistenceService

        beforeTest {
            properties = InMemoryProperties(property)
            eventTypes = InMemoryEventTypes()
            store = RecordingStore()
            service = EventPersistenceService(properties, eventTypes, store)
        }

        fun pending(
            name: String = "page-view",
            domain: String = "example.com",
            sessionId: String? = "session-1",
            props: Map<String, Any?> = mapOf("plan" to "pro"),
            country: String? = "NL",
            headers: Map<String, String> = mapOf("user-agent" to "Mozilla/5.0"),
            receivedAt: Instant = now,
        ) = PendingEvent(
            eventId = EventId(UUID.randomUUID()),
            receivedAt = receivedAt,
            command =
                RegisterEventCommand(
                    domain = domain,
                    name = name,
                    sessionId = sessionId,
                    url = "https://example.com/home",
                    referrer = "https://search.example",
                    screenWidth = 1920,
                    screenHeight = 1080,
                    language = "en-US",
                    userAgent = "Mozilla/5.0",
                    props = props,
                    country = country,
                    headers = headers,
                ),
        )

        context("persist") {
            test("writes the whole batch with a single store call and returns the row count") {
                val written = service.persist(listOf(pending(), pending(sessionId = "session-2")))

                written shouldBe 2
                store.calls shouldHaveSize 1
                store.calls.single() shouldHaveSize 2
            }

            test("resolves properties for the whole batch in one lookup") {
                service.persist(listOf(pending(), pending(sessionId = "session-2")))

                properties.batchLookups shouldHaveSize 1
                properties.batchLookups.single() shouldBe setOf("example.com")
                properties.singleLookups shouldBe 0
            }

            test("maps every queued event onto its property and server timestamp") {
                val queued = pending(receivedAt = now.plusSeconds(3))
                service.persist(listOf(queued))

                val stored = store.calls.single().single()
                stored.id shouldBe queued.eventId
                stored.propertyId shouldBe property.id
                stored.name shouldBe "page-view"
                stored.sessionId?.value shouldBe "session-1"
                stored.props shouldBe mapOf("plan" to "pro")
                stored.country shouldBe "NL"
                stored.headers shouldBe mapOf("user-agent" to "Mozilla/5.0")
                stored.createdAt shouldBe now.plusSeconds(3)
            }

            test("discards events whose domain has no registered property") {
                val written =
                    service.persist(
                        listOf(
                            pending(),
                            pending(domain = "unknown.example", sessionId = "session-2"),
                        ),
                    )

                written shouldBe 1
                store.calls.single() shouldHaveSize 1
                store.calls
                    .single()
                    .single()
                    .propertyId shouldBe property.id
            }

            test("writes nothing when no domain is registered") {
                service.persist(listOf(pending(domain = "unknown.example"))) shouldBe 0

                store.calls.shouldBeEmpty()
            }

            test("ignores an empty batch") {
                service.persist(emptyList()) shouldBe 0

                store.calls.shouldBeEmpty()
                properties.batchLookups.shouldBeEmpty()
            }

            test("registers each unseen event type once across batches") {
                service.persist(listOf(pending(name = "signup-click"), pending(name = "signup-click")))
                service.persist(listOf(pending(name = "signup-click"), pending(name = "hover")))

                eventTypes.registered.map { it.name } shouldBe listOf("signup-click", "hover")
                eventTypes.bulkCalls shouldBe 2
            }

            test("re-registers an event type if the registration failed") {
                eventTypes.failNextBulkCall = true

                runCatching { service.persist(listOf(pending(name = "signup-click"))) }

                eventTypes.failNextBulkCall = false
                service.persist(listOf(pending(name = "signup-click")))

                eventTypes.registered.map { it.name } shouldBe listOf("signup-click")
            }
        }
    })

private class InMemoryProperties(
    private var property: Property?,
) : PropertyRepositoryPort {
    var singleLookups = 0
    val batchLookups = mutableListOf<Set<String>>()

    override fun all(): List<Property> = listOfNotNull(property)

    override fun byDomain(domain: String): Property? {
        singleLookups++
        return property?.takeIf { it.domain == domain }
    }

    override fun byDomains(domains: Set<String>): Map<String, Property> {
        batchLookups += domains
        return listOfNotNull(property).filter { it.domain in domains }.associateBy { it.domain }
    }

    override fun save(property: Property) {
        this.property = property
    }
}

private class InMemoryEventTypes : EventTypeRepositoryPort {
    val registered = mutableListOf<EventType>()
    var bulkCalls = 0
    var failNextBulkCall = false

    override fun registerIfAbsent(
        propertyId: PropertyId,
        name: String,
        description: String?,
        now: Instant,
    ): EventType =
        registered.firstOrNull { it.propertyId == propertyId && it.name == name }
            ?: EventType(EventTypeId(UUID.randomUUID()), propertyId, name, description, now)
                .also { registered += it }

    override fun registerAllIfAbsent(
        propertyId: PropertyId,
        names: Set<String>,
        now: Instant,
    ): List<EventType> {
        bulkCalls++
        if (failNextBulkCall) {
            failNextBulkCall = false
            throw IllegalStateException("registration failed")
        }
        return names.map { registerIfAbsent(propertyId, it, null, now) }
    }

    override fun listForDomain(domain: String): List<EventType> = registered.toList()
}

private class RecordingStore : EventStorePort {
    val calls = mutableListOf<List<AnalyticsEvent>>()

    override fun saveAll(events: List<AnalyticsEvent>): Int {
        calls += events
        return events.size
    }
}
