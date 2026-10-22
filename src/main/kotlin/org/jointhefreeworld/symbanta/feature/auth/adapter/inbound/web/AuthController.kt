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

package org.jointhefreeworld.symbanta.feature.auth.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.jointhefreeworld.symbanta.openapi.controllers.AuthLoginController
import org.jointhefreeworld.symbanta.openapi.controllers.AuthLogoutController
import org.jointhefreeworld.symbanta.openapi.controllers.AuthMeController
import org.jointhefreeworld.symbanta.openapi.models.AuthResponseDto
import org.jointhefreeworld.symbanta.openapi.models.LoginRequestDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Inbound adapter around the framework's authentication machinery. The actual credential
 * lookup happens behind [org.jointhefreeworld.symbanta.feature.auth.port.outbound.UserAccountRepositoryPort].
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val httpRequest: HttpServletRequest,
) : AuthLoginController,
    AuthLogoutController,
    AuthMeController {
    @PostMapping("/login")
    override fun login(
        @RequestBody loginRequest: LoginRequestDto,
    ): ResponseEntity<AuthResponseDto> {
        val authentication =
            try {
                authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password),
                )
            } catch (e: AuthenticationException) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        // Store security context in session cookie (JSESSIONID)
        val session: HttpSession = httpRequest.getSession(true)
        session.setAttribute("SPRING_SECURITY_CONTEXT", context)

        val authority = authentication.authorities.firstOrNull()?.authority ?: "ROLE_USER"
        return ResponseEntity.ok(AuthResponseDto(username = authentication.name, role = authority))
    }

    @PostMapping("/logout")
    override fun logout(): ResponseEntity<Map<String, String?>> {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    @GetMapping("/me")
    override fun getCurrentUser(): ResponseEntity<AuthResponseDto> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated || auth.principal == "anonymousUser") {
            return ResponseEntity.status(401).build()
        }
        val authority = auth.authorities.firstOrNull()?.authority ?: "ROLE_USER"
        return ResponseEntity.ok(AuthResponseDto(username = auth.name, role = authority))
    }
}
