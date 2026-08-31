package com.AirDrop.Spherical.Configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/campfire/**").authenticated()
                        .anyRequest().permitAll()
                )
                // 🟢 Filter now injected properly as a Spring Bean
                .addFilterBefore(gatewayHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

@Component
class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    // 🟢 SECURE PRACTICE: No fallback strings. Fails fast if missing.
    @Value("${ghost.shield.key}")
    private String expectedShieldKey;

    @Value("${ghost.gateway.secret}")
    private String expectedGatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Enforce Gateway/Shield Security Handshake
        String shieldKey = request.getHeader("X-Ghost-Shield-Key");
        String gatewaySecret = request.getHeader("X-Gateway-Secret");

        if (shieldKey != null) shieldKey = shieldKey.trim();
        if (gatewaySecret != null) gatewaySecret = gatewaySecret.trim();

        boolean isShieldValid = shieldKey != null && shieldKey.equals(expectedShieldKey);
        boolean isGatewayValid = gatewaySecret != null && gatewaySecret.equals(expectedGatewaySecret);

        if (!isShieldValid && !isGatewayValid) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("Access Denied: Secure Handshake Failed.");
            return;
        }

        // 2. Extract Authenticated User Identity
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId.trim().toLowerCase(), null, new ArrayList<>());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}