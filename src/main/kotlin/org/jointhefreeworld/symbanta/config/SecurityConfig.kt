package org.jointhefreeworld.symbanta.config

import org.jointhefreeworld.symbanta.UserDetailsService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val userDetailsService: UserDetailsService,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { session -> session.maximumSessions(1).maxSessionsPreventsLogin(false) }
            .rememberMe { rememberMe ->
                rememberMe
                    .userDetailsService(userDetailsService)
                    .key("uniqueAndSecretKey")
                    .tokenValiditySeconds(30 * 24 * 60 * 60)
            }.authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/auth/**", "/v1/events/register", "/symbanta.js", "/error")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasAuthority("ROLE_ADMIN")
                    .anyRequest()
                    .authenticated()
            }

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    @Throws(Exception::class)
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()

        // Analytics ingestion endpoint: Accepts requests from any external website
        val eventsCorsConfig =
            CorsConfiguration().apply {
                allowedOriginPatterns = listOf("*")
                allowedMethods = listOf("GET", "POST", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        source.registerCorsConfiguration("/v1/events/**", eventsCorsConfig)

        // Internal Auth & Admin APIs: Restricted to frontend application domains
        val apiCorsConfig =
            CorsConfiguration().apply {
                allowedOriginPatterns =
                    listOf(
                        "http://localhost:5173",
                        "http://localhost:3000",
                        "https://symbanta.jointhefreeworld.org",
                        "https://api.symbanta.jointhefreeworld.org",
                    )
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        source.registerCorsConfiguration("/api/auth/**", apiCorsConfig)
        source.registerCorsConfiguration("/v1/stats/**", apiCorsConfig)
        source.registerCorsConfiguration("/v1/properties", apiCorsConfig)

        return source
    }
}
