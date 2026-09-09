package org.jointhefreeworld.symbanta.controllers

import org.jointhefreeworld.symbanta.openapi.controllers.V1PropertiesController
import org.jointhefreeworld.symbanta.openapi.models.V1PropertiesResponseDto
import org.jointhefreeworld.symbanta.openapi.models.V1PropertyDto
import org.jointhefreeworld.symbanta.repositories.PostgresPropertiesRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class V1PropertiesControllerImpl(
    private val propertiesRepository: PostgresPropertiesRepository,
) : V1PropertiesController {
    override fun getProperties(): ResponseEntity<V1PropertiesResponseDto> {
        val properties =
            propertiesRepository.getProperties().map {
                V1PropertyDto(id = it.id.value, domain = it.domain, createdAt = it.createdAt, updatedAt = it.updatedAt)
            }
        return ResponseEntity.ok(
            V1PropertiesResponseDto(
                properties = properties,
            ),
        )
    }
}
