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
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.domain.model.Property
import org.jointhefreeworld.symbanta.domain.model.PropertyId
import org.jointhefreeworld.symbanta.support.TestDatabase
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PostgresPropertiesRepositorySpec :
    FunSpec({

        val repository = PostgresPropertiesRepository(TestDatabase.jdbcTemplate)
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS)

        beforeSpec {
            TestDatabase.migrate()
        }

        beforeTest {
            TestDatabase.truncate()
        }

        context("save and byDomain handle various property states correctly") {
            data class SaveTestCase(
                val label: String,
                val domain: String,
                val createdAt: Instant,
                val updatedAt: Instant?,
            )

            withTests(
                SaveTestCase("Basic property with null updatedAt", "example.com", baseTime, null),
                SaveTestCase(
                    "Property with explicit updatedAt",
                    "app.symbanta.org",
                    baseTime,
                    baseTime.plusSeconds(3600),
                ),
                SaveTestCase(
                    "Subdomain with historical timestamp",
                    "sub.domain.io",
                    baseTime.minusSeconds(86400),
                    baseTime,
                ),
                SaveTestCase("Unicode-ish domain", "ünïcode.example", baseTime, null),
            ) { (_, domain, createdAt, updatedAt) ->
                val property =
                    Property(
                        id = PropertyId(UUID.randomUUID()),
                        domain = domain,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )

                repository.save(property)

                val retrieved = repository.byDomain(domain)
                retrieved?.id shouldBe property.id
                retrieved?.domain shouldBe property.domain
                retrieved?.createdAt shouldBe createdAt
                retrieved?.updatedAt shouldBe updatedAt
            }
        }

        context("save updates existing property on ID conflict (upsert behavior)") {
            val propertyId = PropertyId(UUID.randomUUID())

            data class UpsertTestCase(
                val label: String,
                val domain: String,
                val updatedAt: Instant?,
            )

            withTests(
                UpsertTestCase("Initial save", "initial-domain.com", null),
                UpsertTestCase("Updated domain and timestamp", "updated-domain.com", baseTime.plusSeconds(1800)),
            ) { (_, domain, updatedAt) ->
                val property =
                    Property(
                        id = propertyId,
                        domain = domain,
                        createdAt = baseTime,
                        updatedAt = updatedAt,
                    )

                repository.save(property)

                val fetched = repository.byDomain(domain)
                fetched?.id shouldBe propertyId
                fetched?.domain shouldBe domain
                fetched?.updatedAt shouldBe updatedAt
            }
        }

        test("all() fetches every saved record") {
            val properties =
                listOf(
                    Property(PropertyId(UUID.randomUUID()), "alpha.com", baseTime, null),
                    Property(PropertyId(UUID.randomUUID()), "beta.com", baseTime, baseTime),
                )

            properties.forEach { repository.save(it) }

            val result = repository.all()
            result.map { it.domain } shouldBe listOf("alpha.com", "beta.com")
        }

        test("byDomain returns null when the record is missing") {
            repository.byDomain("nonexistent.com").shouldBeNull()
        }

        test("all() returns an empty list when the database is empty") {
            repository.all().shouldBeEmpty()
        }
    })
