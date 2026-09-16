package org.jointhefreeworld.symbanta.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.jointhefreeworld.symbanta.domain.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class PostgresAnalyticsRepositorySpec :
    FunSpec({

        // Container and DataSource Setup
        val postgres =
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("symbanta_analytics_test")
                withUsername("test_user")
                withPassword("test_password")
                start()
            }

        val dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName("org.postgresql.Driver")
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }

        val jdbcTemplate = NamedParameterJdbcTemplate(dataSource)
        val repository = PostgresAnalyticsRepository(jdbcTemplate)

        val baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS)

        // Lifecycle Hooks
        beforeSpec {
            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()
        }

        beforeTest {
            jdbcTemplate.jdbcTemplate.execute("TRUNCATE TABLE events, properties CASCADE;")
        }

        afterSpec {
            postgres.stop()
        }

        // Helper method to seed a Property
        fun createAndSaveProperty(
            domain: String = "example.com",
            id: PropertyId = PropertyId(UUID.randomUUID()),
        ): Property {
            val property = Property(id = id, domain = domain, createdAt = baseTime, updatedAt = null)
            jdbcTemplate.jdbcTemplate.execute(
                "INSERT INTO properties (id, domain, created_at) VALUES ('${property.id.value}', '${property.domain}', '$baseTime')",
            )
            return property
        }

        // Tests

        context("getPropertyByDomain") {
            test("returns property when domain exists") {
                val property = createAndSaveProperty(domain = "analytics.org")

                val result = repository.getPropertyByDomain("analytics.org")

                result.shouldNotBeNull()
                result.id shouldBe property.id
                result.domain shouldBe property.domain
            }

            test("returns null when domain does not exist") {
                repository.getPropertyByDomain("nonexistent.org").shouldBeNull()
            }
        }

        context("saveEvent") {
            test("persists analytics event correctly") {
                val property = createAndSaveProperty()
                val eventId = EventId(UUID.randomUUID())
                val event =
                    AnalyticsEvent(
                        id = eventId,
                        propertyId = property.id,
                        sessionId = SessionId("session-123"),
                        eventName = "page-view",
                        url = "https://example.com/home",
                        referrer = "https://google.com",
                        screenWidth = 1920,
                        screenHeight = 1080,
                        language = "en-US",
                        userAgent = "Mozilla/5.0",
                        propsJson = """{"source": "campaign"}""",
                        createdAt = baseTime,
                    )

                val savedId = repository.saveEvent(event)

                savedId shouldBe eventId

                val count =
                    jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM events WHERE id = :id",
                        mapOf("id" to eventId.value),
                        Int::class.java,
                    )
                count shouldBe 1
            }
        }

        context("getStatsSummary") {
            test("calculates pageViews, visitors, bounceRate, and averageDurationMillis correctly") {
                val property = createAndSaveProperty("statstest.com")
                val from = baseTime.minusSeconds(3600)
                val to = baseTime.plusSeconds(3600)

                val session1 = SessionId("session-1")
                val session2 = SessionId("session-2")

                // Session 1: 2 events spaced by 5 seconds (active duration 5000 ms, not a bounce)
                repository.saveEvent(
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = session1,
                        eventName = "page-view",
                        url = "https://statstest.com",
                        referrer = null,
                        screenWidth = null,
                        screenHeight = null,
                        language = null,
                        userAgent = null,
                        propsJson = null,
                        createdAt = baseTime,
                    ),
                )
                repository.saveEvent(
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = session1,
                        eventName = "page-view",
                        url = "https://statstest.com/about",
                        referrer = null,
                        screenWidth = null,
                        screenHeight = null,
                        language = null,
                        userAgent = null,
                        propsJson = null,
                        createdAt = baseTime.plusSeconds(5),
                    ),
                )

                // Session 2: 1 event (bounce, 0 duration)
                repository.saveEvent(
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = session2,
                        eventName = "page-view",
                        url = "https://statstest.com",
                        referrer = null,
                        screenWidth = null,
                        screenHeight = null,
                        language = null,
                        userAgent = null,
                        propsJson = null,
                        createdAt = baseTime,
                    ),
                )

                val summary = repository.getStatsSummary("statstest.com", from, to)

                summary.pageViews shouldBe 3L
                summary.visitors shouldBe 2L
                summary.bounceRate.compareTo(BigDecimal("50.00")) shouldBe 0
                summary.averageDurationMillis.toDouble() shouldBe 5000.0
            }
        }

        context("getTimeSeriesStats") {
            test("returns aggregated stats grouped by granularity time buckets") {
                val property = createAndSaveProperty("timeseries.com")
                val from = baseTime.truncatedTo(ChronoUnit.HOURS)
                val to = from.plusSeconds(7200)

                repository.saveEvent(
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = SessionId("s1"),
                        eventName = "page-view",
                        url = "https://timeseries.com",
                        referrer = null,
                        screenWidth = null,
                        screenHeight = null,
                        language = null,
                        userAgent = null,
                        propsJson = null,
                        createdAt = from.plusSeconds(300),
                    ),
                )

                val result =
                    repository.getTimeSeriesStats(
                        domain = "timeseries.com",
                        from = from,
                        to = to,
                        granularity = TimeGranularity.HOUR,
                    )

                result.shouldNotBeNull()
                result.first().pageViews shouldBe 1L
                result.first().visitors shouldBe 1L
            }
        }

        context("getScreenSizeStats") {
            test("aggregates screen dimensions ordered by pageViews desc") {
                val property = createAndSaveProperty("screens.com")
                val from = baseTime.minusSeconds(100)
                val to = baseTime.plusSeconds(100)

                // 2 page views on 1920x1080
                repeat(2) {
                    repository.saveEvent(
                        AnalyticsEvent(
                            id = EventId(UUID.randomUUID()),
                            propertyId = property.id,
                            sessionId = SessionId("s-$it"),
                            eventName = "page-view",
                            url = "https://screens.com",
                            referrer = null,
                            screenWidth = 1920,
                            screenHeight = 1080,
                            language = null,
                            userAgent = null,
                            propsJson = null,
                            createdAt = baseTime,
                        ),
                    )
                }

                // 1 page view on 1280x720
                repository.saveEvent(
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = SessionId("s-3"),
                        eventName = "page-view",
                        url = "https://screens.com",
                        referrer = null,
                        screenWidth = 1280,
                        screenHeight = 720,
                        language = null,
                        userAgent = null,
                        propsJson = null,
                        createdAt = baseTime,
                    ),
                )

                val stats = repository.getScreenSizeStats("screens.com", from, to)

                stats shouldHaveSize 2
                stats[0].width shouldBe 1920
                stats[0].height shouldBe 1080
                stats[0].pageViews shouldBe 2L

                stats[1].width shouldBe 1280
                stats[1].height shouldBe 720
                stats[1].pageViews shouldBe 1L
            }
        }

        context("getUserAgentStats") {
            test("aggregates user agents ordered by pageViews desc") {
                val property = createAndSaveProperty("useragents.com")
                val from = baseTime.minusSeconds(100)
                val to = baseTime.plusSeconds(100)

                repository.saveEvent(
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = SessionId("s1"),
                        eventName = "page-view",
                        url = "https://useragents.com",
                        referrer = null,
                        screenWidth = null,
                        screenHeight = null,
                        language = null,
                        userAgent = "Firefox",
                        propsJson = null,
                        createdAt = baseTime,
                    ),
                )

                val stats = repository.getUserAgentStats("useragents.com", from, to)

                stats shouldHaveSize 1
                stats.first().userAgent shouldBe "Firefox"
                stats.first().pageViews shouldBe 1L
            }

            test("returns empty list when domain has no stats") {
                val stats =
                    repository.getUserAgentStats(
                        "empty.com",
                        baseTime.minusSeconds(100),
                        baseTime.plusSeconds(100),
                    )
                stats.shouldBeEmpty()
            }
        }
    })
