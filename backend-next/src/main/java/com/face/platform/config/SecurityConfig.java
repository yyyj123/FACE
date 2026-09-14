package com.face.platform.config;

import com.face.platform.security.TenantContextFilter;
import com.face.platform.security.AdminSurfaceGuardFilter;
import com.face.platform.integration.IntegrationClientFilter;
import com.face.platform.v3.api.RequestIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        TenantContextFilter tenantContextFilter,
        AdminSurfaceGuardFilter adminSurfaceGuardFilter,
        IntegrationClientFilter integrationClientFilter,
        RequestIdFilter requestIdFilter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health/**", "/api/v2/health").permitAll()
                .anyRequest().permitAll()
            )
            .addFilterBefore(tenantContextFilter, AnonymousAuthenticationFilter.class)
            .addFilterBefore(adminSurfaceGuardFilter, TenantContextFilter.class)
            .addFilterBefore(integrationClientFilter, TenantContextFilter.class)
            .addFilterBefore(requestIdFilter, IntegrationClientFilter.class)
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${face.cors.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList()
        );
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Token", "Idempotency-Key", "X-Request-Id",
            "X-Integration-Client", "X-Integration-Timestamp", "X-Integration-Nonce",
            AdminSurfaceGuardFilter.SURFACE_HEADER
        ));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
