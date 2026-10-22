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
