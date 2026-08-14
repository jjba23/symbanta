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
    fun flyway(dataSource: DataSource): Flyway = Flyway.configure().dataSource(dataSource).load()
}
