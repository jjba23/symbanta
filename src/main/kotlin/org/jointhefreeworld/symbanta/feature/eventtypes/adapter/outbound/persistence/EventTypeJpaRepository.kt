/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EventTypeJpaRepository : JpaRepository<EventTypeEntity, UUID> {
    fun findByPropertyIdAndName(
        propertyId: UUID,
        name: String,
    ): EventTypeEntity?

    @Query(
        """
        select et from EventTypeEntity et
        where et.propertyId in (select p.id from PropertyEntity p where p.domain = :domain)
        order by et.name
        """,
    )
    fun findAllForDomain(
        @Param("domain") domain: String,
    ): List<EventTypeEntity>
}
