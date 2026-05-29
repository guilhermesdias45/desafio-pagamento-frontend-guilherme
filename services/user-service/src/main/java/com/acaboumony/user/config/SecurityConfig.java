package com.acaboumony.user.config;

import com.acaboumony.user.security.InternalSecretFilter;
import com.acaboumony.user.security.JwtAuthenticationFilter;
import com.acaboumony.user.security.ProblemDetailsAccessDeniedHandler;
import com.acaboumony.user.security.ProblemDetailsAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final InternalSecretFilter internalSecretFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailsAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(InternalSecretFilter internalSecretFilter,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          ProblemDetailsAuthenticationEntryPoint authenticationEntryPoint,
                          ProblemDetailsAccessDeniedHandler accessDeniedHandler) {
        this.internalSecretFilter = internalSecretFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public routes — no JWT required
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/confirm-email",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/resend-confirmation",
                                "/api/v1/auth/2fa/verify",
                                "/api/v1/auth/2fa/recovery",
                                "/actuator/health",
                                "/api-docs/**",
                                "/swagger-ui/**"
                        ).permitAll()
                        // Internal routes — protected by InternalSecretFilter, not JWT
                        .requestMatchers("/internal/**").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // InternalSecretFilter runs before JWT filter so /internal/** is rejected early
                .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
