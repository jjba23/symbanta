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

package org.jointhefreeworld.symbanta.adapter.inbound.security

import org.jointhefreeworld.symbanta.application.port.outbound.UserAccountRepositoryPort
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
