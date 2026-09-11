package org.jointhefreeworld.symbanta

import org.jointhefreeworld.symbanta.repositories.UserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user =
            userRepository.findByUsername(username)
                ?: throw UsernameNotFoundException("User not found: $username")

        return User
            .builder()
            .username(user.username ?: throw IllegalStateException("User has no username"))
            .password(user.password ?: throw IllegalStateException("User has no password"))
            .authorities(user.role ?: "ROLE_USER")
            .build()
    }
}
