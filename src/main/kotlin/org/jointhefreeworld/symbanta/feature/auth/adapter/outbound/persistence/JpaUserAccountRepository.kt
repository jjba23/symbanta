/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccount
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.springframework.transaction.annotation.Transactional

/**
 * User accounts implemented with Spring Data JPA. Saving an existing username updates the
 * password hash and role instead of inserting a duplicate.
 */
open class JpaUserAccountRepository(
    private val repository: UserAccountJpaRepository,
) : UserAccountRepositoryPort {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun findByUsername(username: String): UserAccount? = repository.findByUsername(username)?.toDomain()

    @Transactional
    override fun save(account: UserAccount): UserAccount {
        val entity = repository.findByUsername(account.username) ?: UserAccountEntity(username = account.username)

        entity.password = account.passwordHash
        entity.role = account.role
        val saved = repository.save(entity)

        logger.debug { "Saved user account '${saved.username}'" }
        return saved.toDomain()
    }
}

internal fun UserAccountEntity.toDomain(): UserAccount =
    UserAccount(
        id = id,
        username = username,
        passwordHash = password,
        role = role,
    )
