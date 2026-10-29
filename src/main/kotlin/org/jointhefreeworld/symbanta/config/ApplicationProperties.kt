/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

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
    /** How long a worker waits for its batch to fill before writing it (0 disables batching wait). */
    val lingerMillis: Long = 10,
)

@ConfigurationProperties(prefix = "application.stats")
data class StatisticsCacheConfig(
    /** How long a stats read is served from memory; 0 disables the cache and reads every call. */
    val cacheTtl: Duration = Duration.ofSeconds(15),
)

@ConfigurationProperties(prefix = "application.property-seeder")
data class PropertySeederConfig(
    val properties: Set<PropertySeederConfigItem> = setOf(),
)

data class PropertySeederConfigItem(
    val domain: String,
)
