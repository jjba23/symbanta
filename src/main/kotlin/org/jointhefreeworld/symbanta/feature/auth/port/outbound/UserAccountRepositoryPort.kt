/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.port.outbound

data class UserAccount(
    val id: Long?,
    val username: String,
    val passwordHash: String,
    val role: String,
)

/** Driven port consumed by the Spring Security inbound adapter. */
interface UserAccountRepositoryPort {
    fun findByUsername(username: String): UserAccount?

    fun save(account: UserAccount): UserAccount
}
