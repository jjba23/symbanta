/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.port.inbound

import org.jointhefreeworld.symbanta.feature.properties.domain.Property

interface ListPropertiesUseCase {
    fun listProperties(): List<Property>
}
