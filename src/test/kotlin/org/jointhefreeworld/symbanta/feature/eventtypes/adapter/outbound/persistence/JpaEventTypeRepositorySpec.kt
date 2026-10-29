/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class JpaEventTypeRepositorySpec :
    FunSpec({

        val repository = TestPersistence.eventTypes
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

        beforeSpec {
            TestDatabase.migrate()
        }

        beforeTest {
            TestDatabase.truncate()
        }

        fun createProperty(domain: String): Property {
            val property = Property(PropertyId(UUID.randomUUID()), domain, now, null)
            TestPersistence.propertyRepository.save(property)
            return property
        }

        test("registers a new event type") {
            val property = createProperty("example.com")

            val registered = repository.registerIfAbsent(property.id, "signup-click", "Completed signup", now)

            registered.name shouldBe "signup-click"
            registered.description shouldBe "Completed signup"
            registered.propertyId shouldBe property.id
            registered.createdAt shouldBe now
        }

        test("is idempotent: re-registering keeps the original row") {
            val property = createProperty("example.com")

            val first = repository.registerIfAbsent(property.id, "signup-click", "Completed signup", now)
            val second =
                repository.registerIfAbsent(property.id, "signup-click", "different description", now.plusSeconds(60))

            second.id shouldBe first.id
            second.createdAt shouldBe first.createdAt
            second.description shouldBe "Completed signup"
        }

        test("registers a set of names, ignoring blanks and duplicates") {
            val property = createProperty("example.com")

            val registered =
                repository.registerAllIfAbsent(property.id, setOf("click", " click ", "", "signup-click"), now)

            registered.map { it.name } shouldBe listOf("click", "signup-click")
            repository.listForDomain("example.com").map { it.name } shouldBe listOf("click", "signup-click")
        }

        test("lists event types per domain, sorted by name") {
            val example = createProperty("example.com")
            val other = createProperty("other.com")

            repository.registerIfAbsent(example.id, "signup-click", null, now)
            repository.registerIfAbsent(example.id, "click", null, now)
            repository.registerIfAbsent(other.id, "checkout", null, now)

            repository.listForDomain("example.com").map { it.name } shouldBe listOf("click", "signup-click")
            repository.listForDomain("other.com").map { it.name } shouldBe listOf("checkout")
            repository.listForDomain("unknown.com").shouldBeEmpty()
        }
    })
