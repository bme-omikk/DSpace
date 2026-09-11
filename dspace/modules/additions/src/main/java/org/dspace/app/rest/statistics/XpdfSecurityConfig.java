package org.dspace.app.rest.statistics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class XpdfSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain xpdfFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/xpdf/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
