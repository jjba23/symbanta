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

package org.jointhefreeworld.symbanta.adapter.inbound.seeder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.application.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.config.PropertySeederConfig
import org.jointhefreeworld.symbanta.domain.model.Property
import org.jointhefreeworld.symbanta.domain.model.PropertyId
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
