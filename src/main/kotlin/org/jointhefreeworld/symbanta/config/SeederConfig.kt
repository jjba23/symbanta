/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.config

import org.jointhefreeworld.symbanta.feature.auth.adapter.inbound.seeder.UserSeeder
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.PasswordHasherPort
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.jointhefreeworld.symbanta.feature.properties.adapter.inbound.seeder.PropertySeeder
import org.jointhefreeworld.symbanta.feature.properties.port.outbound.PropertyRepositoryPort
import org.jointhefreeworld.symbanta.shared.port.outbound.ClockPort
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("seeder")
class SeederConfig {
    @Bean
    fun propertySeeder(
        properties: PropertyRepositoryPort,
        config: PropertySeederConfig,
        clock: ClockPort,
    ): CommandLineRunner = PropertySeeder(properties, config, clock)

    @Bean
    fun userSeeder(
        users: UserAccountRepositoryPort,
        passwordHasher: PasswordHasherPort,
        config: UserSeederConfig,
    ): CommandLineRunner = UserSeeder(users, passwordHasher, config)
}
