/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Row of the `properties` table.
 *
 * The identifier is assigned by the application, so there is no generated value and no
 * association: referencing another table by id keeps aggregates free of lazy loading.
 */
@Entity
@Table(name = "properties")
class PropertyEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "domain", nullable = false, unique = true)
    var domain: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at")
    var updatedAt: Instant? = null,
)
