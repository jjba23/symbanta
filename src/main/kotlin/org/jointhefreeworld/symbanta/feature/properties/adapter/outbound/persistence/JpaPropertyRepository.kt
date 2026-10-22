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

package org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional

/**
 * Property port implemented with Spring Data JPA.
 */
open class JpaPropertyRepository(
    private val repository: PropertyJpaRepository,
) : PropertyRepositoryPort {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    override fun all(): List<Property> {
        logger.debug { "Fetching properties from database" }
        return repository.findAll(Sort.by("domain")).map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun byDomain(domain: String): Property? {
        logger.debug { "Fetching property by domain: $domain" }
        return repository.findByDomain(domain)?.toDomain()
    }

    @Transactional(readOnly = true)
    override fun byDomains(domains: Set<String>): Map<String, Property> {
        if (domains.isEmpty()) return emptyMap()

        logger.debug { "Fetching ${domains.size} properties by domain" }
        return repository.findByDomainIn(domains).associate { it.domain to it.toDomain() }
    }

    @Transactional
    override fun save(property: Property) {
        logger.info { "Saving property with ID: ${property.id.value}" }

        val entity =
            repository.findById(property.id.value).orElseGet {
                PropertyEntity(id = property.id.value, createdAt = property.createdAt)
            }

        entity.domain = property.domain
        entity.updatedAt = property.updatedAt
        repository.save(entity)
    }
}

internal fun PropertyEntity.toDomain(): Property =
    Property(
        id = PropertyId(id),
        domain = domain,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
