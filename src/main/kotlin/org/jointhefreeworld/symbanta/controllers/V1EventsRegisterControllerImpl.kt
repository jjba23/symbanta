package org.jointhefreeworld.symbanta.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jointhefreeworld.symbanta.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.domain.AnalyticsRepository
import org.jointhefreeworld.symbanta.domain.EventId
import org.jointhefreeworld.symbanta.domain.SessionId
import org.jointhefreeworld.symbanta.openapi.controllers.V1EventsRegisterController
import org.jointhefreeworld.symbanta.openapi.models.V1EventPayloadDto
import org.jointhefreeworld.symbanta.openapi.models.V1EventResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.util.*

@RestController
class V1EventsRegisterControllerImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val clock: Clock,
) : V1EventsRegisterController {
    private val logger = KotlinLogging.logger { }
    private val objectMapper = jacksonObjectMapper()

    // Dedicated scope for background processing (Dispatchers.IO handles blocking JDBC calls)
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun registerEvent(v1EventPayload: V1EventPayloadDto): ResponseEntity<V1EventResponseDto> {
        // Fire-and-forget background execution
        backgroundScope.launch {
            try {
                val property =
                    checkNotNull(analyticsRepository.getPropertyByDomain(v1EventPayload.domain)) {
                        "Could not find property for wanted domain: ${v1EventPayload.domain}"
                    }

                val parsedPayload =
                    AnalyticsEvent(
                        id = EventId(UUID.randomUUID()),
                        propertyId = property.id,
                        sessionId = v1EventPayload.sessionId?.let { SessionId(it) },
                        eventName = v1EventPayload.name,
                        url = v1EventPayload.url?.toString(),
                        referrer = v1EventPayload.referrer?.toString(),
                        screenWidth = v1EventPayload.screenWidth,
                        screenHeight = v1EventPayload.screenHeight,
                        language = v1EventPayload.language,
                        userAgent = v1EventPayload.userAgent,
                        propsJson = objectMapper.writeValueAsString(v1EventPayload.props),
                        createdAt = clock.instant(),
                    )
                analyticsRepository.saveEvent(parsedPayload)
            } catch (e: Exception) {
                logger.error(e) { "Failed to process event" }
            }
        }

        // Immediate HTTP response
        return ResponseEntity.ok(V1EventResponseDto(message = "Event received and queued"))
    }
}
