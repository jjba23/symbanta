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
