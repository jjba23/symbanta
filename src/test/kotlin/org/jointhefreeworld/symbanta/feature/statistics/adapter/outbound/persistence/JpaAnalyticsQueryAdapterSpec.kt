/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence

import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPortContract
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.AnalyticsQueryFixture
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence

class JpaAnalyticsQueryAdapterSpec : AnalyticsQueryPortContract(JpaAnalyticsQueryFixture())

/** Seeds the JPA adapter through the real event store, so it exercises written rows. */
class JpaAnalyticsQueryFixture : AnalyticsQueryFixture {
    override fun createQueries(): AnalyticsQueryPort {
        TestDatabase.migrate()
        return TestPersistence.analytics
    }

    override fun createProperty(domain: String): Property {
        TestDatabase.migrate()
        val property = AnalyticsFixtures.property(domain)
        TestPersistence.propertyRepository.save(property)
        return property
    }

    override fun write(events: List<AnalyticsEvent>) {
        TestPersistence.eventStore.saveAll(events)
    }

    override fun reset() = TestDatabase.truncate()
}
