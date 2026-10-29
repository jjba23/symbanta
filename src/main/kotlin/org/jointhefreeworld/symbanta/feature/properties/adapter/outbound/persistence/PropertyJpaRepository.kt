/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PropertyJpaRepository : JpaRepository<PropertyEntity, UUID> {
    fun findByDomain(domain: String): PropertyEntity?

    fun findByDomainIn(domains: Collection<String>): List<PropertyEntity>
}
