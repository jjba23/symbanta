/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.port.outbound

import org.jointhefreeworld.symbanta.feature.properties.domain.Property

interface PropertyRepositoryPort {
    fun all(): List<Property>

    fun byDomain(domain: String): Property?

    /**
     * Resolves all properties for the given domains in one query. Domains without a property are
     * absent from the result.
     */
    fun byDomains(domains: Set<String>): Map<String, Property>

    fun save(property: Property)
}
