/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.eventtypes.adapter.inbound.web

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.TestPrelude.fixedClock
import org.jointhefreeworld.symbanta.feature.eventtypes.domain.EventType
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.ListEventTypesUseCase
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.RegisterEventTypeCommand
import org.jointhefreeworld.symbanta.feature.eventtypes.port.inbound.RegisterEventTypeUseCase
import org.jointhefreeworld.symbanta.shared.adapter.inbound.web.GlobalExceptionControllerAdvice
import org.jointhefreeworld.symbanta.shared.domain.EventTypeId
import org.jointhefreeworld.symbanta.shared.domain.PropertyId
import org.jointhefreeworld.symbanta.shared.domain.PropertyNotFoundException
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class EventTypesControllerSpec :
    FunSpec({

        val register = mock<RegisterEventTypeUseCase>()
        val list = mock<ListEventTypesUseCase>()

        val propertyId = PropertyId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val eventType =
            EventType(
                id = EventTypeId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                propertyId = propertyId,
                name = "signup-click",
                description = "User completed the signup form",
                createdAt = fixedClock.instant(),
            )

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(EventTypesController(register, list))
                .setControllerAdvice(GlobalExceptionControllerAdvice::class.java)
                .build()

        beforeTest {
            reset(register, list)
        }

        context("GET /v1/event-types") {
            test("returns the registered event types for a domain") {
                whenever(list.list("example.com")).thenReturn(listOf(eventType))

                mockMvc
                    .get("/v1/event-types") {
                        param("domain", "example.com")
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.eventTypes[0].id") { value("22222222-2222-2222-2222-222222222222") }
                        jsonPath("$.eventTypes[0].name") { value("signup-click") }
                        jsonPath("$.eventTypes[0].description") { value("User completed the signup form") }
                    }
            }

            test("returns an empty list when nothing is registered") {
                whenever(list.list(any())).thenReturn(emptyList())

                mockMvc
                    .get("/v1/event-types") {
                        param("domain", "example.com")
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.eventTypes") { isEmpty() }
                    }
            }
        }

        context("POST /v1/event-types") {
            test("registers a custom event type and returns 201 Created") {
                whenever(register.register(any())).thenReturn(eventType)

                mockMvc
                    .post("/v1/event-types") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            // language=JSON
                            """
                            {"domain":"example.com","name":"signup-click","description":"User completed the signup form"}
                            """.trimIndent()
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isCreated() }
                        jsonPath("$.name") { value("signup-click") }
                        jsonPath("$.createdAt") { exists() }
                    }

                val captor = argumentCaptor<RegisterEventTypeCommand>()
                verify(register).register(captor.capture())
                captor.firstValue shouldBe
                    RegisterEventTypeCommand(
                        domain = "example.com",
                        name = "signup-click",
                        description = "User completed the signup form",
                    )
            }

            test("returns 404 Not Found when the property is unknown") {
                whenever(register.register(any())).thenThrow(PropertyNotFoundException("unknown.org"))

                mockMvc
                    .post("/v1/event-types") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"domain":"unknown.org","name":"signup-click"}"""
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isNotFound() }
                    }
            }

            test("returns 400 Bad Request when the event name is rejected") {
                whenever(register.register(any()))
                    .thenThrow(
                        org.jointhefreeworld.symbanta.shared.domain
                            .EventValidationException("bad name"),
                    )

                mockMvc
                    .post("/v1/event-types") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"domain":"example.com","name":" "}"""
                        accept = MediaType.APPLICATION_JSON
                    }.andExpect {
                        status { isBadRequest() }
                    }
            }
        }
    })
