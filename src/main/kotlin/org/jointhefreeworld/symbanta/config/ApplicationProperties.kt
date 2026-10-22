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
