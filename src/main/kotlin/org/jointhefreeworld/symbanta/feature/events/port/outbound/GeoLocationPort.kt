/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
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
