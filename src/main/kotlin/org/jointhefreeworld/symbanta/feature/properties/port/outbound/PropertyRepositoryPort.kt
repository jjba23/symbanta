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
