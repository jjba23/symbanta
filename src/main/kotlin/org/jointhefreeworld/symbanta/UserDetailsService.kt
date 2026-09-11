package org.jointhefreeworld.symbanta

import org.jointhefreeworld.symbanta.repositories.UserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Service

@Service
class UserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)!!

        return User
            .builder()
            .username(user.username!!)
            .password(user.password!!)
            .authorities(user.role ?: "ROLE_USER")
            .build()
    }
}
