package com.acaboumony.fraud.config;

import com.acaboumony.fraud.security.InternalSecretFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the fraud-service.
 *
 * <p>All {@code /internal/**} routes are protected by {@link InternalSecretFilter}
 * using a shared machine-to-machine secret. No JWT authentication is required —
 * only service-to-service calls via the api-gateway reach this service.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final InternalSecretFilter internalSecretFilter;

    public SecurityConfig(InternalSecretFilter internalSecretFilter) {
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/metrics"
                        ).permitAll()
                        // Internal routes — protected by InternalSecretFilter
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
