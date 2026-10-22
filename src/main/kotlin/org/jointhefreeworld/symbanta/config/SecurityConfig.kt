package org.jointhefreeworld.symbanta.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/auth/**", "/v1/events/register", "/symbanta.js", "/error")
                    .permitAll()
                    // Probes and the Prometheus scrape endpoint stay reachable without a browser
                    // session; every other actuator endpoint requires an administrator.
                    .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/prometheus/**")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers("/admin/**")
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
    fun daoAuthenticationProvider(
        userDetailsService: UserDetailsService,
        passwordEncoder: PasswordEncoder,
    ): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return provider
    }

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
        source.registerCorsConfiguration("/auth/**", apiCorsConfig)
        source.registerCorsConfiguration("/v1/stats/**", apiCorsConfig)
        source.registerCorsConfiguration("/v1/properties", apiCorsConfig)

        return source
    }
}
