package org.jointhefreeworld.symbanta.controllers

import org.jointhefreeworld.symbanta.openapi.controllers.*
import org.jointhefreeworld.symbanta.openapi.models.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class V1EventsRegisterControllerImpl : V1EventsRegisterController {
    override fun registerEvent(v1EventPayload: V1EventPayloadDto): ResponseEntity<V1EventResponseDto> =
        ResponseEntity.ok(V1EventResponseDto(message = "Event received and queued"))
}
