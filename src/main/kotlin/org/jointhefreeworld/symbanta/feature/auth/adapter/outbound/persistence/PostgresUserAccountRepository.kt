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

import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccount
import org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet

// Spring proxies @Transactional adapters with CGLIB, so these classes must be open.
open class PostgresUserAccountRepository(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : UserAccountRepositoryPort {
    private val rowMapper =
        RowMapper { rs: ResultSet, _ ->
            UserAccount(
                id = rs.getLong("id"),
                username = rs.getString("username"),
                passwordHash = rs.getString("password"),
                role = rs.getString("role") ?: "ROLE_USER",
            )
        }

    @Transactional(readOnly = true)
    override fun findByUsername(username: String): UserAccount? =
        namedParameterJdbcTemplate
            .query(findSql, MapSqlParameterSource("username", username), rowMapper)
            .firstOrNull()

    @Transactional
    override fun save(account: UserAccount): UserAccount {
        val params =
            MapSqlParameterSource()
                .addValue("username", account.username)
                .addValue("password", account.passwordHash)
                .addValue("role", account.role)

        return namedParameterJdbcTemplate.query(saveSql, params, rowMapper).single()
    }

    private companion object {
        val findSql =
            """
            SELECT id, username, password, role FROM app_users WHERE username = :username
            """.trimIndent()

        val saveSql =
            """
            INSERT INTO app_users (username, password, role)
            VALUES (:username, :password, :role)
            ON CONFLICT (username) DO UPDATE
            SET password = EXCLUDED.password,
                role = EXCLUDED.role
            RETURNING id, username, password, role
            """.trimIndent()
    }
}
