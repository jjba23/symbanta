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

package org.jointhefreeworld.symbanta.feature.events.port.outbound

/**
 * Driven port: coarse geolocation of the client.
 *
 * Symbanta never stores the client IP address. Implementations only return an ISO-3166
 * alpha-2 country code (for example from a CDN header or a local GeoIP database), or null
 * when the location is unknown.
 */
interface GeoLocationPort {
    fun countryOf(
        headers: Map<String, String>,
        remoteAddress: String?,
    ): String?
}
