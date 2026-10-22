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

import io.micrometer.core.instrument.MeterRegistry
import org.jointhefreeworld.symbanta.feature.auth.adapter.inbound.security.DatabaseUserDetailsService
import org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.persistence.PostgresUserAccountRepository
import org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.security.BCryptPasswordHasher
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.PasswordHasherPort
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.jointhefreeworld.symbanta.feature.console.adapter.outbound.persistence.PostgresConsoleLogAdapter
import org.jointhefreeworld.symbanta.feature.console.port.outbound.ConsoleLogQueryPort
import org.jointhefreeworld.symbanta.feature.console.service.ConsoleLogService
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.geo.HttpHeaderGeoLocationAdapter
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.PostgresEventStore
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.queue.BatchingEventDispatcher
import org.jointhefreeworld.symbanta.feature.events.port.inbound.EventPersistenceUseCase
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventQueuePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.events.port.outbound.GeoLocationPort
import org.jointhefreeworld.symbanta.feature.events.service.EventIngestionService
import org.jointhefreeworld.symbanta.feature.events.service.EventPersistenceService
import org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence.PostgresEventTypeRepository
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.feature.eventtypes.service.EventTypeService
import org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence.PostgresPropertiesRepository
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.service.PropertyService
import org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence.PostgresAnalyticsQueryAdapter
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.feature.statistics.service.StatisticsQueryService
import org.jointhefreeworld.symbanta.shared.adapter.outbound.time.SystemClockAdapter
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * The composition root.
 *
 * Adapters and use cases carry no framework annotations: this class decides which
 * implementation satisfies which port. Swapping PostgreSQL for another store, or the in-process
 * queue for a broker, is a change here and nowhere else.
 */
@Configuration
class WiringConfig {
    // ----- Driven (outbound) adapters -----

    @Bean
    fun clockPort(): ClockPort = SystemClockAdapter()

    @Bean
    fun geoLocationPort(): GeoLocationPort = HttpHeaderGeoLocationAdapter()

    @Bean
    fun passwordHasherPort(passwordEncoder: PasswordEncoder): PasswordHasherPort = BCryptPasswordHasher(passwordEncoder)

    @Bean
    fun eventStorePort(jdbc: NamedParameterJdbcTemplate): EventStorePort = PostgresEventStore(jdbc)

    @Bean
    fun analyticsQueryPort(jdbc: NamedParameterJdbcTemplate): AnalyticsQueryPort = PostgresAnalyticsQueryAdapter(jdbc)

    @Bean
    fun consoleLogQueryPort(jdbc: NamedParameterJdbcTemplate): ConsoleLogQueryPort = PostgresConsoleLogAdapter(jdbc)

    @Bean
    fun propertyRepositoryPort(jdbc: NamedParameterJdbcTemplate): PropertyRepositoryPort = PostgresPropertiesRepository(jdbc)

    @Bean
    fun eventTypeRepositoryPort(jdbc: NamedParameterJdbcTemplate): EventTypeRepositoryPort = PostgresEventTypeRepository(jdbc)

    @Bean
    fun userAccountRepositoryPort(jdbc: NamedParameterJdbcTemplate): UserAccountRepositoryPort = PostgresUserAccountRepository(jdbc)

    // ----- Ingestion pipeline -----

    @Bean
    fun eventPersistenceUseCase(
        properties: PropertyRepositoryPort,
        eventTypes: EventTypeRepositoryPort,
        eventStore: EventStorePort,
    ): EventPersistenceUseCase = EventPersistenceService(properties, eventTypes, eventStore)

    @Bean(destroyMethod = "shutdown")
    fun eventQueuePort(
        persistence: EventPersistenceUseCase,
        meterRegistry: MeterRegistry,
        ingestion: IngestionConfig,
    ): BatchingEventDispatcher =
        BatchingEventDispatcher(
            persistence = persistence,
            meterRegistry = meterRegistry,
            queueCapacity = ingestion.queueCapacity,
            workerCount = ingestion.workerCount,
            maxBatchSize = ingestion.maxBatchSize,
        )

    @Bean
    fun eventIngestionService(
        queue: EventQueuePort,
        clock: ClockPort,
    ): EventIngestionService = EventIngestionService(queue, clock)

    // ----- Application services (other inbound port implementations) -----

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
    fun consoleLogService(logs: ConsoleLogQueryPort): ConsoleLogService = ConsoleLogService(logs)

    // ----- Framework SPI adapters -----

    @Bean
    fun userDetailsService(users: UserAccountRepositoryPort): UserDetailsService = DatabaseUserDetailsService(users)
}
