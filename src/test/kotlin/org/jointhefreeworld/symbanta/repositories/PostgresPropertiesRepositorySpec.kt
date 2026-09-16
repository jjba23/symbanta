package org.jointhefreeworld.symbanta.repositories

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.domain.Property
import org.jointhefreeworld.symbanta.domain.PropertyId
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class PostgresPropertiesRepositorySpec :
    FunSpec({

        // Container and DataSource Setup
        val postgres =
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("symbanta_test")
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
        val repository = PostgresPropertiesRepository(jdbcTemplate)

        // Truncate sub-microsecond precision for PostgreSQL TIMESTAMPTZ compatibility
        val baseTime = Instant.now().truncatedTo(ChronoUnit.MICROS)

        // Lifecycle Hooks
        beforeSpec {
            jdbcTemplate.jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS properties (
                    id UUID PRIMARY KEY,
                    domain VARCHAR(255) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ
                );
                """.trimIndent(),
            )
        }

        beforeTest {
            jdbcTemplate.jdbcTemplate.execute("TRUNCATE TABLE properties;")
        }

        afterSpec {
            postgres.stop()
        }

        // Data-Driven Tests

        context("save and findByDomain handles various property states correctly") {
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
            ) { (label, domain, createdAt, updatedAt) ->
                val property =
                    Property(
                        id = PropertyId(UUID.randomUUID()),
                        domain = domain,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                    )

                repository.save(property)

                val retrieved = repository.findByDomain(domain)
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
            ) { (label, domain, updatedAt) ->
                val property =
                    Property(
                        id = propertyId,
                        domain = domain,
                        createdAt = baseTime,
                        updatedAt = updatedAt,
                    )

                repository.save(property)

                val fetched = repository.findByDomain(domain)
                fetched?.id shouldBe propertyId
                fetched?.domain shouldBe domain
                fetched?.updatedAt shouldBe updatedAt
            }
        }

        test("getProperties fetches all saved records") {
            val properties =
                listOf(
                    Property(PropertyId(UUID.randomUUID()), "alpha.com", baseTime, null),
                    Property(PropertyId(UUID.randomUUID()), "beta.com", baseTime, baseTime),
                )

            properties.forEach { repository.save(it) }

            val result = repository.getProperties()
            result.first().id shouldBe properties.first().id
            result.first().domain shouldBe properties.first().domain

            result[1].id shouldBe properties[1].id
            result[1].domain shouldBe properties[1].domain
        }

        test("findByDomain returns null when record is missing") {
            repository.findByDomain("nonexistent.com").shouldBeNull()
        }

        test("getProperties returns empty list when database is empty") {
            repository.getProperties().shouldBeEmpty()
        }
    })
