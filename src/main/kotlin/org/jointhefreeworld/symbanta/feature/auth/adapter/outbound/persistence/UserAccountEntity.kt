/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.feature.auth.adapter.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Row of the `app_users` table. The identity column is database generated.
 */
@Entity
@Table(name = "app_users")
class UserAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,
    @Column(name = "username", nullable = false, unique = true)
    var username: String = "",
    @Column(name = "password", nullable = false)
    var password: String = "",
    @Column(name = "role")
    var role: String = "ROLE_USER",
)
