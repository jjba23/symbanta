/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import org.jointhefreeworld.symbanta.feature.events.port.inbound.IngestEventUseCase
import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.feature.events.port.outbound.GeoLocationPort
import org.jointhefreeworld.symbanta.openapi.controllers.V1EventsRegisterController
import org.jointhefreeworld.symbanta.openapi.models.V1EventPayloadDto
import org.jointhefreeworld.symbanta.openapi.models.V1EventResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP ingestion adapter.
 *
 * The request itself is enriched here — allow-listed headers (#7), the coarse country of the
 * caller, and fallbacks from `Referer`/`User-Agent`/`Accept-Language` — before the application
 * layer sees a transport-free command.
 */
@RestController
class EventRegistrationController(
    private val ingest: IngestEventUseCase,
    private val geoLocation: GeoLocationPort,
    private val httpRequest: HttpServletRequest,
) : V1EventsRegisterController {
    override fun registerEvent(v1EventPayload: V1EventPayloadDto): ResponseEntity<V1EventResponseDto> {
        val headers = RequestHeaders.persisted(httpRequest)
        // Country lookup may read any header (CDN country hints are not part of the persisted set).
        val country = geoLocation.countryOf(RequestHeaders.all(httpRequest), httpRequest.remoteAddr)

        ingest.register(
            RegisterEventCommand(
                domain = v1EventPayload.domain,
                name = v1EventPayload.name,
                sessionId = v1EventPayload.sessionId,
                url = v1EventPayload.url?.toString(),
                referrer = v1EventPayload.referrer?.toString() ?: headers[RequestHeaders.REFERER],
                screenWidth = v1EventPayload.screenWidth,
                screenHeight = v1EventPayload.screenHeight,
                language = v1EventPayload.language ?: headers[RequestHeaders.ACCEPT_LANGUAGE]?.substringBefore(','),
                userAgent = v1EventPayload.userAgent ?: headers[RequestHeaders.USER_AGENT],
                props = v1EventPayload.props.orEmpty(),
                country = country,
                headers = headers,
            ),
        )

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(V1EventResponseDto(message = "Event accepted and queued"))
    }
}

/**
 * Allow-list of request headers persisted alongside an event.
 *
 * Cookies, Authorization and the client IP are deliberately never captured: the country is
 * derived in memory and only that coarse value is stored.
 */
object RequestHeaders {
    const val USER_AGENT = "user-agent"
    const val ACCEPT_LANGUAGE = "accept-language"
    const val REFERER = "referer"

    private const val MAX_VALUE_LENGTH = 512

    private val ALLOWED =
        setOf(
            USER_AGENT,
            ACCEPT_LANGUAGE,
            REFERER,
            "origin",
            "accept",
            "content-type",
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "sec-ch-ua-platform",
            "dnt",
            "sec-gpc",
        )

    /** Every request header, lowercased: used for in-memory lookups only, never persisted as-is. */
    fun all(request: HttpServletRequest): Map<String, String> =
        request.headerNames
            ?.toList()
            .orEmpty()
            .associate { it.lowercase() to (request.getHeader(it) ?: "").take(MAX_VALUE_LENGTH) }

    /** The allow-listed subset that is persisted with the event. */
    fun persisted(request: HttpServletRequest): Map<String, String> = all(request).filterKeys { it in ALLOWED }
}
