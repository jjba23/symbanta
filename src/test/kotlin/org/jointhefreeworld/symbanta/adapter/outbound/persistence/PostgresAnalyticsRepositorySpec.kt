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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.domain.model.AnalyticsEvent
import org.jointhefreeworld.symbanta.domain.model.ConsoleLogLevel
import org.jointhefreeworld.symbanta.domain.model.DateRange
import org.jointhefreeworld.symbanta.domain.model.EventId
import org.jointhefreeworld.symbanta.domain.model.EventNames
import org.jointhefreeworld.symbanta.domain.model.Property
import org.jointhefreeworld.symbanta.domain.model.PropertyId
import org.jointhefreeworld.symbanta.domain.model.SessionId
import org.jointhefreeworld.symbanta.domain.model.TimeGranularity
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class PostgresAnalyticsRepositorySpec :
    FunSpec({

        val jdbcTemplate = TestDatabase.jdbcTemplate
        val repository = PostgresAnalyticsRepository(jdbcTemplate)
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS)

        beforeSpec {
            TestDatabase.migrate()
        }

        beforeTest {
            TestDatabase.truncate()
        }

        fun createAndSaveProperty(domain: String = "example.com"): Property {
            val property = Property(PropertyId(UUID.randomUUID()), domain, baseTime, null)
            jdbcTemplate.update(
                "INSERT INTO properties (id, domain, created_at) VALUES (:id, :domain, :createdAt)",
                MapSqlParameterSource()
                    .addValue("id", property.id.value)
                    .addValue("domain", property.domain)
                    .addValue("createdAt", java.sql.Timestamp.from(baseTime)),
            )
            return property
        }

        fun event(
            property: Property,
            name: String = EventNames.PAGE_VIEW,
            sessionId: String? = "session-1",
            url: String = "https://${property.domain}/",
            screenWidth: Int? = null,
            screenHeight: Int? = null,
            userAgent: String? = null,
            props: Map<String, Any?> = emptyMap(),
            country: String? = null,
            headers: Map<String, String> = emptyMap(),
            createdAt: Instant = baseTime,
        ) = AnalyticsEvent(
            id = EventId(UUID.randomUUID()),
            propertyId = property.id,
            sessionId = sessionId?.let(::SessionId),
            name = name,
            url = url,
            referrer = null,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            language = null,
            userAgent = userAgent,
            props = props,
            country = country,
            headers = headers,
            createdAt = createdAt,
        )

        fun range(
            from: Instant = baseTime.minusSeconds(3600),
            to: Instant = baseTime.plusSeconds(3600),
        ) = DateRange(from, to)

        context("save") {
            test("persists the event together with country, headers and props (#7)") {
                val property = createAndSaveProperty()
                val event =
                    event(
                        property,
                        country = "NL",
                        headers = mapOf("user-agent" to "Mozilla/5.0", "accept-language" to "en-US"),
                        props = mapOf("plan" to "pro"),
                    )

                repository.save(event) shouldBe event.id

                val row =
                    jdbcTemplate.queryForMap(
                        """
                        SELECT country,
                               headers ->> 'user-agent' AS user_agent,
                               headers ->> 'accept-language' AS accept_language,
                               props ->> 'plan' AS plan
                        FROM events WHERE id = :id
                        """.trimIndent(),
                        mapOf("id" to event.id.value),
                    )
                row["country"] shouldBe "NL"
                row["user_agent"] shouldBe "Mozilla/5.0"
                row["accept_language"] shouldBe "en-US"
                row["plan"] shouldBe "pro"
            }

            test("persists an event without optional context") {
                val property = createAndSaveProperty()

                repository.save(event(property))

                val count =
                    jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM events",
                        emptyMap<String, Any>(),
                        Int::class.java,
                    )
                count shouldBe 1
            }
        }

        context("summary") {
            test("calculates pageViews, visitors, bounceRate, and averageDurationMillis") {
                val property = createAndSaveProperty("statstest.com")
                val from = baseTime.minusSeconds(3600)
                val to = baseTime.plusSeconds(3600)

                // Session 1: two events 5s apart (active for 5s, not a bounce)
                repository.save(event(property, sessionId = "session-1", url = "https://statstest.com", createdAt = baseTime))
                repository.save(
                    event(
                        property,
                        sessionId = "session-1",
                        url = "https://statstest.com/about",
                        createdAt = baseTime.plusSeconds(5),
                    ),
                )
                // Session 2: single event (bounce)
                repository.save(event(property, sessionId = "session-2", createdAt = baseTime))

                val summary = repository.summary("statstest.com", DateRange(from, to))

                summary.pageViews shouldBe 3
                summary.visitors shouldBe 2
                summary.bounceRate.compareTo(BigDecimal("50.00")) shouldBe 0
                summary.averageDurationMillis.toDouble() shouldBe 5000.0
            }

            test("returns zeroes for an unknown domain") {
                val summary = repository.summary("missing.com", range())

                summary.pageViews shouldBe 0
                summary.visitors shouldBe 0
            }
        }

        context("timeSeries") {
            test("returns aggregated stats grouped by hour") {
                val property = createAndSaveProperty("timeseries.com")
                val from = baseTime.truncatedTo(ChronoUnit.HOURS)
                val to = from.plusSeconds(7200)

                repository.save(event(property, createdAt = from.plusSeconds(300)))

                val result = repository.timeSeries("timeseries.com", DateRange(from, to), TimeGranularity.HOUR)

                result.first().pageViews shouldBe 1
                result.first().visitors shouldBe 1
            }
        }

        context("screenSizes") {
            test("aggregates raw screen dimensions ordered by pageViews desc") {
                val property = createAndSaveProperty("screens.com")

                repeat(2) {
                    repository.save(
                        event(property, sessionId = "s-$it", screenWidth = 1920, screenHeight = 1080),
                    )
                }
                repository.save(event(property, sessionId = "s-3", screenWidth = 1280, screenHeight = 720))

                val stats = repository.screenSizes("screens.com", range())

                stats shouldHaveSize 2
                stats[0].width shouldBe 1920
                stats[0].height shouldBe 1080
                stats[0].pageViews shouldBe 2
                stats[1].width shouldBe 1280
                stats[1].pageViews shouldBe 1
            }
        }

        context("userAgents") {
            test("aggregates user agents ordered by pageViews desc") {
                val property = createAndSaveProperty("useragents.com")

                repository.save(event(property, userAgent = "Firefox"))

                val stats = repository.userAgents("useragents.com", range())

                stats shouldHaveSize 1
                stats.first().userAgent shouldBe "Firefox"
                stats.first().pageViews shouldBe 1
            }

            test("returns an empty list when there is no data") {
                repository.userAgents("empty.com", range()).shouldBeEmpty()
            }
        }

        context("routes") {
            test("aggregates routes ordered by pageViews desc") {
                val property = createAndSaveProperty("routes.com")
                val someEvent = event(property, sessionId = "s1", url = "https://routes.com/")

                repository.save(someEvent)
                repository.save(someEvent.copy(id = EventId(UUID.randomUUID()), createdAt = baseTime.plusSeconds(30)))
                repository.save(
                    someEvent.copy(
                        id = EventId(UUID.randomUUID()),
                        url = "https://routes.com/blog",
                        sessionId = SessionId("s2"),
                        createdAt = baseTime.plusSeconds(10),
                    ),
                )

                val stats = repository.routes("routes.com", range())

                stats shouldHaveSize 2
                stats.first().route shouldBe "/"
                stats.first().pageViews shouldBe 2
                stats.drop(1).first().route shouldBe "/blog"
            }
        }

        context("events (#4)") {
            test("counts occurrences per event name, including custom events") {
                val property = createAndSaveProperty("custom.com")

                repository.save(event(property, name = EventNames.PAGE_VIEW, sessionId = "s1"))
                repository.save(event(property, name = EventNames.PAGE_VIEW, sessionId = "s2"))
                repository.save(event(property, name = "signup-click", sessionId = "s1"))
                repository.save(event(property, name = "hover", sessionId = "s1"))

                val stats = repository.events("custom.com", range())

                stats.map { it.eventName } shouldBe listOf("page-view", "hover", "signup-click")
                stats.first().occurrences shouldBe 2
                stats.first().visitors shouldBe 2
                stats.first { it.eventName == "signup-click" }.visitors shouldBe 1
            }
        }

        context("countries (#7)") {
            test("groups page views by stored country, defaulting to ZZ") {
                val property = createAndSaveProperty("geo.com")

                repository.save(event(property, sessionId = "s1", country = "NL"))
                repository.save(event(property, sessionId = "s2", country = "NL"))
                repository.save(event(property, sessionId = "s3", country = "DE"))
                repository.save(event(property, sessionId = "s4"))

                val stats = repository.countries("geo.com", range())

                stats.map { it.country } shouldBe listOf("NL", "DE", "ZZ")
                stats.first().pageViews shouldBe 2
                stats.first().visitors shouldBe 2
            }
        }

        context("consoleLogs (#6)") {
            test("returns recent console entries, newest first, filtered by level") {
                val property = createAndSaveProperty("logs.com")

                repository.save(
                    event(
                        property,
                        name = EventNames.CONSOLE,
                        url = "https://logs.com/dashboard",
                        country = "NL",
                        props = mapOf("level" to "error", "message" to "boom"),
                        createdAt = baseTime,
                    ),
                )
                repository.save(
                    event(
                        property,
                        name = EventNames.CONSOLE,
                        sessionId = "session-2",
                        props = mapOf("level" to "warn", "message" to "deprecated"),
                        createdAt = baseTime.plusSeconds(10),
                    ),
                )
                repository.save(event(property, name = EventNames.PAGE_VIEW))

                val all = repository.consoleLogs("logs.com", null, 10)
                all shouldHaveSize 2
                all.first().message shouldBe "deprecated"
                all.first().level shouldBe ConsoleLogLevel.WARN

                val errors = repository.consoleLogs("logs.com", ConsoleLogLevel.ERROR, 10)
                errors shouldHaveSize 1
                errors.first().level shouldBe ConsoleLogLevel.ERROR
                errors.first().message shouldBe "boom"
                errors.first().url shouldBe "https://logs.com/dashboard"
                errors.first().country shouldBe "NL"

                repository.consoleLogs("logs.com", null, 1) shouldHaveSize 1
            }

            test("returns an empty list when no console output was captured") {
                repository.consoleLogs("nothing.com", null, 10).shouldBeEmpty()
            }
        }
    })
