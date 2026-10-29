/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
