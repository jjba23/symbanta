/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.security

import org.jointhefreeworld.symbanta.feature.auth.port.outbound.PasswordHasherPort
import org.springframework.security.crypto.password.PasswordEncoder

/** Delegates hashing to the encoder configured by the security framework adapter. */
class BCryptPasswordHasher(
    private val encoder: PasswordEncoder,
) : PasswordHasherPort {
    override fun hash(rawPassword: String): String = checkNotNull(encoder.encode(rawPassword)) { "Password encoder returned no hash" }
}
