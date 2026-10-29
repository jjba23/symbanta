/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.EventJpaRepository
import org.jointhefreeworld.symbanta.feature.events.adapter.outbound.persistence.JpaEventStore
import org.jointhefreeworld.symbanta.feature.events.port.outbound.EventStorePort
import org.jointhefreeworld.symbanta.feature.statistics.adapter.inbound.web.StatsTimeseriesController
import org.jointhefreeworld.symbanta.feature.statistics.adapter.outbound.persistence.JpaAnalyticsQueryAdapter
import org.jointhefreeworld.symbanta.feature.statistics.port.inbound.GetTimeSeriesUseCase
import org.jointhefreeworld.symbanta.feature.statistics.port.outbound.AnalyticsQueryPort
import org.jointhefreeworld.symbanta.support.TestDatabase
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.security.web.SecurityFilterChain

/**
 * Boots the real application against the test database.
 *
 * Feature specs wire a slice by hand, so they cannot see a duplicated bean name, a class left
 * behind in an old package, a missing adapter registration or an unparsable repository query —
 * those only break the deployment. This one runs the same component scan, auto-configuration,
 * JPA repository bootstrap, Flyway migration and servlet setup as `mvn spring-boot:run`, with the
 * web server on an ephemeral port.
 */
class ApplicationContextSpec :
    FunSpec({
        test("boots every feature with its ports wired to the production adapters") {
            TestDatabase.migrate()

            SpringApplicationBuilder(Application::class.java)
                .web(WebApplicationType.SERVLET)
                .run(
                    "--server.port=0",
                    "--spring.datasource.url=${TestDatabase.url}",
                    "--spring.datasource.username=${TestDatabase.username}",
                    "--spring.datasource.password=${TestDatabase.password}",
                ).use { context ->
                    // Ports resolve to the adapters WiringConfig registers, not to mocks.
                    context.getBean(AnalyticsQueryPort::class.java).shouldBeInstanceOf<JpaAnalyticsQueryAdapter>()
                    context.getBean(EventStorePort::class.java).shouldBeInstanceOf<JpaEventStore>()
                    context.getBean(GetTimeSeriesUseCase::class.java)

                    // Spring Data repositories and the inbound adapters are scanned.
                    context.getBean(EventJpaRepository::class.java)
                    context.getBean(StatsTimeseriesController::class.java)

                    // Security and the servlet container are part of the same context.
                    context.getBean(SecurityFilterChain::class.java)
                    val port = (context as ServletWebServerApplicationContext).webServer?.port ?: 0
                    (port > 0) shouldBe true
                }
        }
    })
