package org.jointhefreeworld.symbanta.repositories

import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class UserSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        if (userRepository.findByUsername("admin") == null) {
            val adminUser =
                AppUser().apply {
                    username = "admin"
                    password = passwordEncoder.encode("password123") // Hashes raw password
                    role = "ROLE_ADMIN"
                }
            userRepository.save(adminUser)
            println("=== Initial admin user created: admin / password123 ===")
        }
    }
}
