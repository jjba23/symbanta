/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface UserAccountJpaRepository : JpaRepository<UserAccountEntity, Long> {
    fun findByUsername(username: String): UserAccountEntity?
}
