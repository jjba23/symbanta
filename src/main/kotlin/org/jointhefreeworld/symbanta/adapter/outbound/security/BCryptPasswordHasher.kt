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

package org.jointhefreeworld.symbanta.adapter.outbound.security

import org.jointhefreeworld.symbanta.application.port.outbound.PasswordHasherPort
import org.springframework.security.crypto.password.PasswordEncoder

/** Delegates hashing to the encoder configured by the security framework adapter. */
class BCryptPasswordHasher(
    private val encoder: PasswordEncoder,
) : PasswordHasherPort {
    override fun hash(rawPassword: String): String = checkNotNull(encoder.encode(rawPassword)) { "Password encoder returned no hash" }
}
