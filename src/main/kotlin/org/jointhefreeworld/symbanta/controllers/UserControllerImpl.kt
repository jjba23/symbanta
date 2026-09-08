package org.jointhefreeworld.symbanta.controllers

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.jointhefreeworld.symbanta.openapi.controllers.ApiAuthLoginController
import org.jointhefreeworld.symbanta.openapi.controllers.ApiAuthLogoutController
import org.jointhefreeworld.symbanta.openapi.controllers.ApiAuthMeController
import org.jointhefreeworld.symbanta.openapi.models.AuthResponseDto
import org.jointhefreeworld.symbanta.openapi.models.LoginRequestDto
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class UserControllerImpl(
    private val authenticationManager: AuthenticationManager,
    private val httpRequest: HttpServletRequest,
) : ApiAuthLoginController,
    ApiAuthLogoutController,
    ApiAuthMeController {
    @PostMapping("/login")
    override fun login(
        @RequestBody loginRequest: LoginRequestDto,
    ): ResponseEntity<AuthResponseDto> {
        val authentication =
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password),
            )

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
