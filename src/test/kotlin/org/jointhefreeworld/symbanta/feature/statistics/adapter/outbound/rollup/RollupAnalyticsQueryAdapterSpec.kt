/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup

import org.jointhefreeworld.symbanta.feature.events.domain.AnalyticsEvent
import org.jointhefreeworld.symbanta.feature.properties.domain.Property
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPortContract
import org.jointhefreeworld.symbanta.support.AnalyticsFixtures
import org.jointhefreeworld.symbanta.support.AnalyticsQueryFixture
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.jointhefreeworld.symbanta.support.TestPersistence

/**
 * The rollup adapter is held to exactly the same contract as the direct-from-events one: same
 * windows, same zero filling, same ordering. The fixture writes through the real event store and
 * then refreshes the rollups, so the adapter under test only ever reads pre-aggregated rows.
 */
class RollupAnalyticsQueryAdapterSpec : AnalyticsQueryPortContract(RollupAnalyticsQueryFixture())

/** Seeds events through the event store, then rolls them up before every test. */
class RollupAnalyticsQueryFixture : AnalyticsQueryFixture {
    override fun createQueries(): AnalyticsQueryPort {
        TestDatabase.migrate()
        return TestPersistence.rollupAnalytics
    }

    override fun createProperty(domain: String): Property {
        TestDatabase.migrate()
        val property = AnalyticsFixtures.property(domain)
        TestPersistence.propertyRepository.save(property)
        return property
    }

    override fun write(events: List<AnalyticsEvent>) {
        TestPersistence.eventStore.saveAll(events)
        TestPersistence.rollupRefresher.refresh()
    }

    override fun reset() = TestDatabase.truncate()
}
