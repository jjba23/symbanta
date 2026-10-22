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
