/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.adapter.inbound.seeder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.config.PropertySeederConfig
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import org.springframework.boot.CommandLineRunner
import java.util.UUID

class PropertySeeder(
    private val properties: PropertyRepositoryPort,
    private val config: PropertySeederConfig,
    private val clock: ClockPort,
) : CommandLineRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        logger.info { "Seeding ${config.properties.size} properties..." }

        config.properties.forEach { seed ->
            if (properties.byDomain(seed.domain) == null) {
                properties.save(
                    Property(
                        id = PropertyId(UUID.randomUUID()),
                        domain = seed.domain,
                        createdAt = clock.now(),
                        updatedAt = null,
                    ),
                )
                logger.info { "Property created: ${seed.domain}" }
            }
        }
    }
}
