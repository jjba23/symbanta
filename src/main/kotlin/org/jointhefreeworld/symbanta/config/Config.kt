package org.jointhefreeworld.symbanta.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

@ConfigurationProperties(prefix = "application.user-seeder")
data class UserSeederConfig(
    val users: Set<UserSeederConfigItem> = setOf(),
)

data class UserSeederConfigItem(
    val username: String,
    val password: String,
    val role: String,
)

@ConfigurationProperties(prefix = "application.property-seeder")
data class PropertySeederConfig(
    val properties: Set<PropertySeederConfigItem> = setOf(),
)

data class PropertySeederConfigItem(
    val domain: String,
)
