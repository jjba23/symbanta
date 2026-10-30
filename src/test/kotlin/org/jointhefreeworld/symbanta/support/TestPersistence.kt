/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.support

import org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.persistence.JpaUserAccountRepository
import org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.persistence.UserAccountJpaRepository
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.jointhefreeworld.symbanta.feature.console.adapter.outbound.persistence.JpaConsoleLogAdapter
import org.jointhefreeworld.symbanta.feature.console.port.outbound.ConsoleLogQueryPort
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.EventJpaRepository
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.JpaEventStore
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence.EventTypeJpaRepository
import org.jointhefreeworld.symbanta.feature.eventtypes.adapter.outbound.persistence.JpaEventTypeRepository
import org.jointhefreeworld.symbanta.feature.eventtypes.port.outbound.EventTypeRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence.JpaPropertyRepository
import org.jointhefreeworld.symbanta.feature.properties.adapter.outbound.persistence.PropertyJpaRepository
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence.JpaAnalyticsQueryAdapter
import org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup.RollupAnalyticsQueryAdapter
import org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.rollup.StatsRollupRefresher
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.shared.adapter.outbound.time.SystemClockAdapter
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.Properties
import javax.sql.DataSource

/**
 * A minimal Spring context for the persistence specs.
 *
 * It wires the same repositories and adapters the application uses, against the Testcontainers
 * datasource, without booting the web application. `@Transactional` on the adapters is therefore
 * exercised through real proxies, including Hibernate's batched flush.
 */
object TestPersistence {
    private val context: AnnotationConfigApplicationContext by lazy {
        AnnotationConfigApplicationContext().apply {
            register(TestPersistenceConfig::class.java)
            refresh()
        }
    }

    val properties: PropertyJpaRepository get() = context.getBean(PropertyJpaRepository::class.java)

    val propertyRepository: PropertyRepositoryPort get() = context.getBean(PropertyRepositoryPort::class.java)

    val events: EventJpaRepository get() = context.getBean(EventJpaRepository::class.java)

    val eventStore: EventStorePort get() = context.getBean(EventStorePort::class.java)

    val eventTypes: EventTypeRepositoryPort get() = context.getBean(EventTypeRepositoryPort::class.java)

    val userAccounts: UserAccountRepositoryPort get() = context.getBean(UserAccountRepositoryPort::class.java)

    // Looked up by name: both analytics beans implement the same port, and this slice uses the
    // default (interface) proxying, so the transactional adapter is a JDK proxy.
    val analytics: AnalyticsQueryPort get() = context.getBean("analyticsQueryPort", AnalyticsQueryPort::class.java)

    val rollupAnalytics: AnalyticsQueryPort get() = context.getBean("rollupAnalyticsQueryAdapter", AnalyticsQueryPort::class.java)

    val rollupRefresher: StatsRollupRefresher get() = context.getBean("statsRollupRefresher", StatsRollupRefresher::class.java)

    val consoleLogs: ConsoleLogQueryPort get() = context.getBean(ConsoleLogQueryPort::class.java)
}

/**
 * Deliberately not annotated with `@Configuration`.
 *
 * It lives inside the application's component-scan package, so a stereotype would also be picked
 * up whenever a spec boots the real application (`ApplicationContextSpec`) and collide with the
 * production beans of the same name. [TestPersistence] registers it explicitly instead; Spring
 * still processes its `@Bean` methods as a lite configuration class.
 */
@EnableJpaRepositories(basePackages = ["org.jointhefreeworld.symbanta.feature"])
@EnableTransactionManagement
class TestPersistenceConfig {
    @Bean
    fun dataSource(): DataSource = TestDatabase.dataSource

    @Bean
    fun entityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean =
        LocalContainerEntityManagerFactoryBean().apply {
            this.dataSource = dataSource
            setPackagesToScan("org.jointhefreeworld.symbanta.feature")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaProperties(
                Properties().apply {
                    setProperty("hibernate.hbm2ddl.auto", "none")
                    setProperty("hibernate.jdbc.batch_size", "500")
                    setProperty("hibernate.order_inserts", "true")
                    setProperty("hibernate.jdbc.time_zone", "UTC")
                },
            )
        }

    @Bean
    fun transactionManager(entityManagerFactory: jakarta.persistence.EntityManagerFactory): PlatformTransactionManager =
        JpaTransactionManager(entityManagerFactory)

    @Bean
    fun propertyRepositoryPort(repository: PropertyJpaRepository): PropertyRepositoryPort = JpaPropertyRepository(repository)

    @Bean
    fun eventStorePort(repository: EventJpaRepository): EventStorePort = JpaEventStore(repository)

    @Bean
    fun eventTypeRepositoryPort(repository: EventTypeJpaRepository): EventTypeRepositoryPort = JpaEventTypeRepository(repository)

    @Bean
    fun userAccountRepositoryPort(repository: UserAccountJpaRepository): UserAccountRepositoryPort = JpaUserAccountRepository(repository)

    @Bean
    fun analyticsQueryPort(
        properties: PropertyRepositoryPort,
        events: EventJpaRepository,
    ): AnalyticsQueryPort = JpaAnalyticsQueryAdapter(properties, events)

    @Bean
    fun namedParameterJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate = NamedParameterJdbcTemplate(dataSource)

    @Bean
    fun clockPort(): ClockPort = SystemClockAdapter()

    @Bean
    fun statsRollupRefresher(
        jdbc: NamedParameterJdbcTemplate,
        clock: ClockPort,
        transactions: PlatformTransactionManager,
    ): StatsRollupRefresher = StatsRollupRefresher(jdbc, clock, transactions)

    @Bean
    fun rollupAnalyticsQueryAdapter(
        jdbc: NamedParameterJdbcTemplate,
        properties: PropertyRepositoryPort,
        refresher: StatsRollupRefresher,
    ): RollupAnalyticsQueryAdapter = RollupAnalyticsQueryAdapter(jdbc, properties, refresher)

    @Bean
    fun consoleLogQueryPort(events: EventJpaRepository): ConsoleLogQueryPort = JpaConsoleLogAdapter(events)
}
