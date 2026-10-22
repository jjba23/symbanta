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

package org.jointhefreeworld.symbanta.feature.console.adapter.outbound.persistence

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.feature.console.domain.ConsoleLogLevel
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.PostgresEventStore
import org.jointhefreeworld.symbanta.feature.events.domain.EventNames
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.BASE_TIME
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.event
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.Timestamp

class PostgresConsoleLogAdapterSpec :
    FunSpec({

        val jdbcTemplate = TestDatabase.jdbcTemplate
        val store = PostgresEventStore(jdbcTemplate)
        val logs = PostgresConsoleLogAdapter(jdbcTemplate)

        beforeSpec {
            TestDatabase.migrate()
        }

        beforeTest {
            TestDatabase.truncate()
        }

        fun createProperty(domain: String): Property {
            val property = AnalyticsFixtures.property(domain)
            jdbcTemplate.update(
                "INSERT INTO properties (id, domain, created_at) VALUES (:id, :domain, :createdAt)",
                MapSqlParameterSource()
                    .addValue("id", property.id.value)
                    .addValue("domain", property.domain)
                    .addValue("createdAt", Timestamp.from(property.createdAt)),
            )
            return property
        }

        test("returns recent console entries, newest first, filtered by level") {
            val property = createProperty("logs.example")

            store.saveAll(
                listOf(
                    event(
                        property,
                        name = EventNames.CONSOLE,
                        url = "https://logs.example/dashboard",
                        country = "NL",
                        props = mapOf("level" to "error", "message" to "boom"),
                        createdAt = BASE_TIME,
                    ),
                    event(
                        property,
                        name = EventNames.CONSOLE,
                        sessionId = "session-2",
                        props = mapOf("level" to "warn", "message" to "deprecated"),
                        createdAt = BASE_TIME.plusSeconds(10),
                    ),
                    event(property, name = EventNames.PAGE_VIEW),
                ),
            )

            val all = logs.recent("logs.example", null, 10)
            all shouldHaveSize 2
            all.first().message shouldBe "deprecated"
            all.first().level shouldBe ConsoleLogLevel.WARN

            val errors = logs.recent("logs.example", ConsoleLogLevel.ERROR, 10)
            errors shouldHaveSize 1
            errors.first().level shouldBe ConsoleLogLevel.ERROR
            errors.first().message shouldBe "boom"
            errors.first().url shouldBe "https://logs.example/dashboard"
            errors.first().country shouldBe "NL"

            logs.recent("logs.example", null, 1) shouldHaveSize 1
        }

        test("returns an empty list when no console output was captured") {
            logs.recent("nothing.example", null, 10).shouldBeEmpty()
        }
    })
