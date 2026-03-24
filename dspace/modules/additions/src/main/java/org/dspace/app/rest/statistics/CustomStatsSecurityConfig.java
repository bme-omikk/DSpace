package org.dspace.app.rest.statistics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class CustomStatsSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain customStatsFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/statistics/bydate", "/api/statistics/bycity")
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                new AntPathRequestMatcher("/api/statistics/bydate"),
                new AntPathRequestMatcher("/api/statistics/bycity")
            ))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
