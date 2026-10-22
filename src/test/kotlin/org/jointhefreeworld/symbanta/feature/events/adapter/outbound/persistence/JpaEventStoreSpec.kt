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

package org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePortContract
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.event
import org.jointhefreeworld.symbanta.support.EventStoreFixture
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence

class JpaEventStoreSpec : EventStorePortContract(JpaEventStoreFixture())

/** Seeding for the JPA store: migrations, properties and row counting. */
class JpaEventStoreFixture : EventStoreFixture {
    override fun createStore(): EventStorePort {
        TestDatabase.migrate()
        return TestPersistence.eventStore
    }

    override fun createProperty(domain: String): Property {
        TestDatabase.migrate()
        val property = AnalyticsFixtures.property(domain)
        TestPersistence.propertyRepository.save(property)
        return property
    }

    override fun reset() = TestDatabase.truncate()

    override fun eventCount(): Long = TestPersistence.events.count()
}

/**
 * Adapter-specific assertions that go beyond the shared contract.
 */
class JpaEventStoreColumnsSpec :
    FunSpec({

        val fixture = JpaEventStoreFixture()
        val store = fixture.createStore()

        beforeSpec {
            TestDatabase.migrate()
        }

        beforeTest {
            fixture.reset()
        }

        test("persists country, headers and props for every event of the batch (#7)") {
            val property = fixture.createProperty("columns.example")
            val first =
                event(
                    property,
                    country = "NL",
                    headers = mapOf("user-agent" to "Mozilla/5.0", "accept-language" to "en-US"),
                    props = mapOf("plan" to "pro"),
                )

            store.saveAll(listOf(first)) shouldBe 1

            val row =
                TestDatabase.jdbcTemplate.queryForMap(
                    """
                    SELECT country,
                           headers ->> 'user-agent' AS user_agent,
                           headers ->> 'accept-language' AS accept_language,
                           props ->> 'plan' AS plan
                    FROM events WHERE id = :id
                    """.trimIndent(),
                    mapOf("id" to first.id.value),
                )

            row["country"] shouldBe "NL"
            row["user_agent"] shouldBe "Mozilla/5.0"
            row["accept_language"] shouldBe "en-US"
            row["plan"] shouldBe "pro"
        }
    })
