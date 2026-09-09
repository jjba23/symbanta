package org.jointhefreeworld.symbanta.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.config.UserSeederConfig
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
@Profile("seeder")
class UserSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userSeederConfig: UserSeederConfig,
) : CommandLineRunner {
    private val logger = KotlinLogging.logger {}

    override fun run(vararg args: String) {
        logger.info { "Seeding ${userSeederConfig.users.size} users..." }
        userSeederConfig.users.forEach { user ->
            if (userRepository.findByUsername(user.username) == null) {
                val myUser =
                    AppUser().apply {
                        username = user.username
                        password = passwordEncoder.encode(user.password)
                        role = user.role
                    }
                userRepository.save(myUser)
                println("=== User created: ${user.username} / ******* / ${user.role}  ===")
            }
        }
    }
}
