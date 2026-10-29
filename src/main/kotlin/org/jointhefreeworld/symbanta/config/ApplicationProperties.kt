/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "application.user-seeder")
data class UserSeederConfig(
    val users: Set<UserSeederConfigItem> = setOf(),
)

data class UserSeederConfigItem(
    val username: String,
    val password: String,
    val role: String,
)

@ConfigurationProperties(prefix = "application.ingestion")
data class IngestionConfig(
    val queueCapacity: Int = 10_000,
    val workerCount: Int = 8,
    /** Upper bound on how many queued events one worker writes in a single batch. */
    val maxBatchSize: Int = 500,
)

@ConfigurationProperties(prefix = "application.property-seeder")
data class PropertySeederConfig(
    val properties: Set<PropertySeederConfigItem> = setOf(),
)

data class PropertySeederConfigItem(
    val domain: String,
)
