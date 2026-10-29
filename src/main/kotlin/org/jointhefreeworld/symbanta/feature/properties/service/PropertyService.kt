/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.service

import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.properties.port.inbound.ListPropertiesUseCase
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort

class PropertyService(
    private val properties: PropertyRepositoryPort,
) : ListPropertiesUseCase {
    override fun listProperties(): List<Property> = properties.all()
}
