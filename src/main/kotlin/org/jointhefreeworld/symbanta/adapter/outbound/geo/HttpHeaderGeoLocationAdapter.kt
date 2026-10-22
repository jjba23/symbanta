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

package org.jointhefreeworld.symbanta.adapter.outbound.geo

import org.jointhefreeworld.symbanta.application.port.outbound.GeoLocationPort

/**
 * Resolves a coarse country code from the headers a reverse proxy or CDN already provides.
 *
 * This keeps Symbanta privacy-friendly and dependency-free: the raw IP address is used at most
 * to look up the country and is never persisted. Deployments behind a GeoIP-capable proxy get
 * country data for free; everyone else simply gets `null`.
 */
class HttpHeaderGeoLocationAdapter : GeoLocationPort {
    override fun countryOf(
        headers: Map<String, String>,
        remoteAddress: String?,
    ): String? {
        val raw =
            COUNTRY_HEADERS.firstNotNullOfOrNull { header ->
                headers[header]?.trim()?.takeIf { it.isNotEmpty() }
            } ?: return null

        val country = raw.uppercase()
        val isIsoAlpha2 = country.length == 2 && country.all { it in 'A'..'Z' }

        return country.takeIf { isIsoAlpha2 && it !in NON_COUNTRY_CODES }
    }

    private companion object {
        /** Common CDN/proxy headers carrying an ISO-3166 alpha-2 country code. */
        val COUNTRY_HEADERS =
            listOf(
                "cf-ipcountry",
                "x-country-code",
                "x-vercel-ip-country",
                "x-appengine-country",
                "cloudfront-viewer-country",
                "x-geo-country",
            )

        /** Values that mean "unknown" rather than an actual country. */
        val NON_COUNTRY_CODES = setOf("XX", "ZZ", "T1")
    }
}
