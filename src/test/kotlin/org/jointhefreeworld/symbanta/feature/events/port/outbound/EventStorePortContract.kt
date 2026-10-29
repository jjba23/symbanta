/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.events.port.outbound

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures.event
import org.jointhefreeworld.symbanta.support.EventStoreFixture

/**
 * Behaviour every [EventStorePort] adapter must satisfy.
 *
 * A new adapter (another database, an object store, an in-memory fake) inherits the suite by
 * passing its [EventStoreFixture]; it then cannot pass its own tests while breaking the batch
 * semantics ingestion relies on.
 */
abstract class EventStorePortContract(
    fixture: EventStoreFixture,
) : FunSpec({

        lateinit var store: EventStorePort

        beforeSpec {
            store = fixture.createStore()
        }

        beforeTest {
            fixture.reset()
            store = fixture.createStore()
        }

        test("writes every event of a batch and reports the number of rows written") {
            val property = fixture.createProperty("store.example")
            val events =
                listOf(
                    event(property, sessionId = "s-1"),
                    event(property, sessionId = "s-2", createdAt = AnalyticsFixtures.BASE_TIME.plusSeconds(1)),
                    event(property, sessionId = "s-3", createdAt = AnalyticsFixtures.BASE_TIME.plusSeconds(2)),
                )

            store.saveAll(events) shouldBe 3
            fixture.eventCount() shouldBe 3L
        }

        test("accepts an empty batch as a no-op") {
            store.saveAll(emptyList()) shouldBe 0
            fixture.eventCount() shouldBe 0L
        }

        test("appends successive batches instead of replacing them") {
            val property = fixture.createProperty("store.example")

            store.saveAll(listOf(event(property, sessionId = "s-1"))) shouldBe 1
            store.saveAll(
                listOf(
                    event(property, sessionId = "s-2", createdAt = AnalyticsFixtures.BASE_TIME.plusSeconds(1)),
                    event(property, sessionId = "s-3", createdAt = AnalyticsFixtures.BASE_TIME.plusSeconds(2)),
                ),
            ) shouldBe 2

            fixture.eventCount() shouldBe 3L
        }
    })
