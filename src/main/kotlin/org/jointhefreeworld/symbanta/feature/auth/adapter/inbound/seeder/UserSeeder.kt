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

package org.jointhefreeworld.symbanta.feature.auth.adapter.inbound.seeder

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.config.UserSeederConfig
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.PasswordHasherPort
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccount
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.springframework.boot.CommandLineRunner

class UserSeeder(
    private val users: UserAccountRepositoryPort,
    private val passwordHasher: PasswordHasherPort,
    private val config: UserSeederConfig,
) : CommandLineRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        logger.info { "Seeding ${config.users.size} users..." }

        config.users.forEach { seed ->
            if (users.findByUsername(seed.username) == null) {
                users.save(
                    UserAccount(
                        id = null,
                        username = seed.username,
                        passwordHash = passwordHasher.hash(seed.password),
                        role = seed.role,
                    ),
                )
                logger.info { "User created: ${seed.username} / ******* / ${seed.role}" }
            }
        }
    }
}
