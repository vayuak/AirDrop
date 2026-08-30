package com.AirDrop.Spherical.Configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/campfire/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new GatewayHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    static class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId.trim().toLowerCase(), null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            filterChain.doFilter(request, response);
        }
    }
}