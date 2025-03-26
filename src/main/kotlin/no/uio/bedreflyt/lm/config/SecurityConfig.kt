package org.smolang.no.uio.bedreflyt.lm.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
open class SecurityConfig {
    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.authorizeHttpRequests { authz ->
            authz
                .requestMatchers(
                    "/",
                    "/api/**",
                    "/api-docs/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v*/api-docs",
                    "/swagger-resources/**").permitAll()
                .anyRequest().authenticated()
        }

        return http.build()
    }

    @Bean
    open fun webSecurityCustomizer () : WebSecurityCustomizer {
        return WebSecurityCustomizer { webSecurity ->
            webSecurity.ignoring().requestMatchers(
                "/websocket",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v*/api-docs/**",
                "/configuration/ui",
                "/swagger-resources/**",
                "/api/**"
            )
        }
    }
}