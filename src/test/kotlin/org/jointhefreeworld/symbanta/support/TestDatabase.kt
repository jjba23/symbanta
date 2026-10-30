/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.support

import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/**
 * One PostgreSQL container shared by every persistence spec: starting a container per spec
 * used to dominate test wall time.
 */
object TestDatabase {
    private val migrated = AtomicBoolean(false)

    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("symbanta_test")
            withUsername("test_user")
            withPassword("test_password")
            start()
        }
    }

    val url: String get() = container.jdbcUrl

    val username: String get() = container.username

    val password: String get() = container.password

    val dataSource: DataSource by lazy {
        val jdbcUrl = url
        val user = username
        val secret = password

        DriverManagerDataSource().apply {
            setDriverClassName("org.postgresql.Driver")
            url = jdbcUrl
            username = user
            password = secret
        }
    }

    val jdbcTemplate: NamedParameterJdbcTemplate by lazy { NamedParameterJdbcTemplate(dataSource) }

    /** Runs the Flyway migrations once for the shared container. */
    fun migrate() {
        if (migrated.compareAndSet(false, true)) {
            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()
        }
    }

    fun truncate() {
        jdbcTemplate.jdbcTemplate.execute(
            "TRUNCATE TABLE events, event_types, properties, app_users, " +
                "stats_session_bucket, stats_dimension_bucket, stats_rollup_state RESTART IDENTITY CASCADE;",
        )
    }
}
