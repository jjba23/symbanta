/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.properties.adapter.inbound.web

import org.jointhefreeworld.symbanta.feature.properties.port.inbound.ListPropertiesUseCase
import org.jointhefreeworld.symbanta.openapi.controllers.V1PropertiesController
import org.jointhefreeworld.symbanta.openapi.models.V1PropertiesResponseDto
import org.jointhefreeworld.symbanta.openapi.models.V1PropertyDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class PropertiesController(
    private val properties: ListPropertiesUseCase,
) : V1PropertiesController {
    override fun getProperties(): ResponseEntity<V1PropertiesResponseDto> =
        ResponseEntity.ok(
            V1PropertiesResponseDto(
                properties =
                    properties.listProperties().map {
                        V1PropertyDto(
                            id = it.id.value,
                            domain = it.domain,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                        )
                    },
            ),
        )
}
