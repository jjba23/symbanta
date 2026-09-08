package org.jointhefreeworld.symbanta.controllers

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

data class LoginRequest(
    val username: String,
    val password: String,
)

data class AuthResponse(
    val username: String,
    val role: String,
)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticationManager: AuthenticationManager,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<AuthResponse> {
        val authentication =
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password),
            )

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        // Store security context in session cookie (JSESSIONID)
        val session: HttpSession = httpRequest.getSession(true)
        session.setAttribute("SPRING_SECURITY_CONTEXT", context)

        val authority = authentication.authorities.firstOrNull()?.authority ?: "ROLE_USER"
        return ResponseEntity.ok(AuthResponse(username = authentication.name, role = authority))
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.ok(mapOf("message" to "Logged out successfully"))
    }

    @GetMapping("/me")
    fun me(): ResponseEntity<AuthResponse> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth == null || !auth.isAuthenticated || auth.principal == "anonymousUser") {
            return ResponseEntity.status(401).build()
        }
        val authority = auth.authorities.firstOrNull()?.authority ?: "ROLE_USER"
        return ResponseEntity.ok(AuthResponse(username = auth.name, role = authority))
    }
}
