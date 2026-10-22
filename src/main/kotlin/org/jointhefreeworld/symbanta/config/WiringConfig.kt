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

package org.jointhefreeworld.symbanta.config

import org.jointhefreeworld.symbanta.adapter.inbound.security.DatabaseUserDetailsService
import org.jointhefreeworld.symbanta.adapter.outbound.geo.HttpHeaderGeoLocationAdapter
import org.jointhefreeworld.symbanta.adapter.outbound.persistence.PostgresAnalyticsRepository
import org.jointhefreeworld.symbanta.adapter.outbound.persistence.PostgresEventTypeRepository
import org.jointhefreeworld.symbanta.adapter.outbound.persistence.PostgresPropertiesRepository
import org.jointhefreeworld.symbanta.adapter.outbound.persistence.PostgresUserAccountRepository
import org.jointhefreeworld.symbanta.adapter.outbound.queue.CoroutineEventDispatcher
import org.jointhefreeworld.symbanta.adapter.outbound.security.BCryptPasswordHasher
import org.jointhefreeworld.symbanta.adapter.outbound.time.SystemClockAdapter
import org.jointhefreeworld.symbanta.application.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.application.port.outbound.ClockPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventDispatchPort
import org.jointhefreeworld.symbanta.application.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.application.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.application.port.outbound.GeoLocationPort
import org.jointhefreeworld.symbanta.application.port.outbound.PasswordHasherPort
import org.jointhefreeworld.symbanta.application.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.application.port.outbound.UserAccountRepositoryPort
import org.jointhefreeworld.symbanta.application.service.ConsoleLogService
import org.jointhefreeworld.symbanta.application.service.EventIngestionService
import org.jointhefreeworld.symbanta.application.service.EventTypeService
import org.jointhefreeworld.symbanta.application.service.PropertyService
import org.jointhefreeworld.symbanta.application.service.StatisticsQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * The composition root.
 *
 * Adapters and use cases carry no framework annotations: this class decides which
 * implementation satisfies which port. Swapping PostgreSQL for another store, or the coroutine
 * dispatcher for a broker, is a change here and nowhere else.
 */
@Configuration
class WiringConfig {
    // ----- Driven (outbound) adapters -----

    @Bean
    fun clockPort(): ClockPort = SystemClockAdapter()

    @Bean(destroyMethod = "shutdown")
    fun eventDispatcher(ingestion: IngestionConfig): CoroutineEventDispatcher =
        CoroutineEventDispatcher(
            queueCapacity = ingestion.queueCapacity,
            workerCount = ingestion.workerCount,
        )

    @Bean
    fun geoLocationPort(): GeoLocationPort = HttpHeaderGeoLocationAdapter()

    @Bean
    fun passwordHasherPort(passwordEncoder: PasswordEncoder): PasswordHasherPort = BCryptPasswordHasher(passwordEncoder)

    /** Satisfies both [EventStorePort] (write side) and [AnalyticsQueryPort] (read side). */
    @Bean
    fun postgresAnalyticsRepository(jdbc: NamedParameterJdbcTemplate): PostgresAnalyticsRepository = PostgresAnalyticsRepository(jdbc)

    @Bean
    fun propertyRepositoryPort(jdbc: NamedParameterJdbcTemplate): PropertyRepositoryPort = PostgresPropertiesRepository(jdbc)

    @Bean
    fun eventTypeRepositoryPort(jdbc: NamedParameterJdbcTemplate): EventTypeRepositoryPort = PostgresEventTypeRepository(jdbc)

    @Bean
    fun userAccountRepositoryPort(jdbc: NamedParameterJdbcTemplate): UserAccountRepositoryPort = PostgresUserAccountRepository(jdbc)

    // ----- Application services (inbound port implementations) -----

    @Bean
    fun eventIngestionService(
        properties: PropertyRepositoryPort,
        eventTypes: EventTypeRepositoryPort,
        eventStore: EventStorePort,
        dispatcher: EventDispatchPort,
        clock: ClockPort,
    ): EventIngestionService =
        EventIngestionService(
            properties = properties,
            eventTypes = eventTypes,
            eventStore = eventStore,
            dispatcher = dispatcher,
            clock = clock,
        )

    @Bean
    fun statisticsQueryService(
        queries: AnalyticsQueryPort,
        clock: ClockPort,
    ): StatisticsQueryService = StatisticsQueryService(queries, clock)

    @Bean
    fun propertyService(properties: PropertyRepositoryPort): PropertyService = PropertyService(properties)

    @Bean
    fun eventTypeService(
        properties: PropertyRepositoryPort,
        eventTypes: EventTypeRepositoryPort,
        clock: ClockPort,
    ): EventTypeService = EventTypeService(properties, eventTypes, clock)

    @Bean
    fun consoleLogService(queries: AnalyticsQueryPort): ConsoleLogService = ConsoleLogService(queries)

    // ----- Framework SPI adapters -----

    @Bean
    fun userDetailsService(users: UserAccountRepositoryPort): UserDetailsService = DatabaseUserDetailsService(users)
}
