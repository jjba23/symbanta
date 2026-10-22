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

package org.jointhefreeworld.symbanta.adapter.outbound.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.domain.model.Property
import org.jointhefreeworld.symbanta.domain.model.PropertyId
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PostgresEventTypeRepositorySpec :
    FunSpec({

        val jdbcTemplate = TestDatabase.jdbcTemplate
        val repository = PostgresEventTypeRepository(jdbcTemplate)
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

        beforeSpec {
            TestDatabase.migrate()
        }

        beforeTest {
            TestDatabase.truncate()
        }

        fun createProperty(domain: String): Property {
            val property = Property(PropertyId(UUID.randomUUID()), domain, now, null)
            jdbcTemplate.update(
                "INSERT INTO properties (id, domain, created_at) VALUES (:id, :domain, :createdAt)",
                MapSqlParameterSource()
                    .addValue("id", property.id.value)
                    .addValue("domain", property.domain)
                    .addValue("createdAt", java.sql.Timestamp.from(now)),
            )
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
