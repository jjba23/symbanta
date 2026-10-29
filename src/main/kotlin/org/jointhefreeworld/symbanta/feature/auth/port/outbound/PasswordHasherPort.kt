/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.port.outbound

/** Driven port: password hashing, implemented by the security framework adapter. */
interface PasswordHasherPort {
    fun hash(rawPassword: String): String
}
