package org.jointhefreeworld.symbanta.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.config.PropertySeederConfig
import org.jointhefreeworld.symbanta.domain.PropertiesRepository
import org.jointhefreeworld.symbanta.domain.Property
import org.jointhefreeworld.symbanta.domain.PropertyId
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.*

@Component
@Profile("seeder")
class PropertySeeder(
    private val propertiesRepository: PropertiesRepository,
    private val propertySeederConfig: PropertySeederConfig,
    private val clock: Clock,
) : CommandLineRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        logger.info { "Seeding ${propertySeederConfig.properties.size} properties..." }
        propertySeederConfig.properties.forEach { property ->
            if (propertiesRepository.findByDomain(property.domain) == null) {
                propertiesRepository.save(
                    Property(
                        id = PropertyId(UUID.randomUUID()),
                        domain = property.domain,
                        createdAt = clock.instant(),
                        updatedAt = null,
                    ),
                )
                println("=== Property created: ${property.domain}  ===")
            }
        }
    }
}
