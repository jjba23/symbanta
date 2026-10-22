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

package org.jointhefreeworld.symbanta.feature.events.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jointhefreeworld.symbanta.feature.events.port.inbound.RegisterEventCommand
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventQueuePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.PendingEvent
import org.jointhefreeworld.symbanta.shared.domain.EventValidationException
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import java.time.Instant

class EventIngestionServiceSpec :
    FunSpec({

        val now = Instant.parse("2026-01-15T00:00:00Z")

        lateinit var queue: RecordingQueue
        lateinit var service: EventIngestionService

        beforeTest {
            queue = RecordingQueue()
            service = EventIngestionService(queue = queue, clock = FixedClock(now))
        }

        fun command(
            name: String = "page-view",
            domain: String = "example.com",
            props: Map<String, Any?> = mapOf("plan" to "pro"),
            country: String? = "NL",
            headers: Map<String, String> = mapOf("user-agent" to "Mozilla/5.0"),
        ) = RegisterEventCommand(
            domain = domain,
            name = name,
            sessionId = "session-1",
            url = "https://example.com/home",
            referrer = "https://search.example",
            screenWidth = 1920,
            screenHeight = 1080,
            language = "en-US",
            userAgent = "Mozilla/5.0",
            props = props,
            country = country,
            headers = headers,
        )

        context("register") {
            test("accepts the event with its request context, props and server timestamp") {
                val eventId = service.register(command())

                queue.pending shouldHaveSize 1
                val pending = queue.pending.single()

                pending.eventId shouldBe eventId
                pending.receivedAt shouldBe now
                pending.command.name shouldBe "page-view"
                pending.command.domain shouldBe "example.com"
                pending.command.sessionId shouldBe "session-1"
                pending.command.props shouldBe mapOf("plan" to "pro")
                pending.command.country shouldBe "NL"
                pending.command.headers shouldBe mapOf("user-agent" to "Mozilla/5.0")
            }

            test("normalises the domain and trims the event name before queueing") {
                service.register(command(name = "  signup-click  ", domain = " Example.COM "))

                queue.pending
                    .single()
                    .command.name shouldBe "signup-click"
                queue.pending
                    .single()
                    .command.domain shouldBe "example.com"
            }

            test("returns a distinct event id per accepted event") {
                val first = service.register(command())
                val second = service.register(command())

                first shouldNotBe second
                queue.pending.map { it.eventId } shouldBe listOf(first, second)
            }

            test("fails fast on an invalid event name without queueing anything") {
                shouldThrow<EventValidationException> {
                    service.register(command(name = "not valid!"))
                }

                queue.pending.shouldBeEmpty()
            }

            test("rejects a blank property domain") {
                shouldThrow<EventValidationException> {
                    service.register(command(domain = "   "))
                }

                queue.pending.shouldBeEmpty()
            }

            test("accepts custom event names, including namespaced ones") {
                service.register(command(name = "checkout:started"))

                queue.pending
                    .single()
                    .command.name shouldBe "checkout:started"
            }
        }
    })

private class RecordingQueue : EventQueuePort {
    val pending = mutableListOf<PendingEvent>()

    override fun enqueue(event: PendingEvent) {
        pending += event
    }
}

private class FixedClock(
    private val instant: Instant,
) : ClockPort {
    override fun now(): Instant = instant
}
