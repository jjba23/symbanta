/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.adapter.inbound.security

import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Inbound adapter from the Spring Security SPI to Symbanta's user account port.
 */
class DatabaseUserDetailsService(
    private val users: UserAccountRepositoryPort,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val account =
            users.findByUsername(username)
                ?: throw UsernameNotFoundException("User not found: $username")

        return User
            .builder()
            .username(account.username)
            .password(account.passwordHash)
            .authorities(account.role)
            .build()
    }
}
