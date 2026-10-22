package org.jointhefreeworld.symbanta.config

import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(
    name = ["spring.flyway.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class FlywayConfig {
    @Bean(initMethod = "migrate")
    fun flyway(dataSource: DataSource): Flyway =
        Flyway
            .configure()
            .dataSource(dataSource)
            // Databases created before V2__add_localhost_property.sql was removed still record it
            // as applied. Without this, validation aborts startup on every such database and the
            // application can never boot again. Only migrations that are absent locally are
            // ignored; checksums of the migrations that do exist are still validated.
            .ignoreMigrationPatterns("*:missing")
            .load()
}
