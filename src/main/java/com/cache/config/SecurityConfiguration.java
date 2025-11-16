package com.cache.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for admin endpoints.
 * Uses environment variables for credentials in production.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Value("${cache.security.admin.username:${CACHE_ADMIN_USERNAME:admin}}")
    private String adminUsername;

    @Value("${cache.security.admin.password:${CACHE_ADMIN_PASSWORD:admin}}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for REST API (use token-based auth in production)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll() // Actuator endpoints public
                .requestMatchers("/", "/index.html", "/dashboard", "/css/**", "/js/**", "/favicon.ico").permitAll() // Static UI resources
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll() // Swagger UI
                .requestMatchers("/api/cache/**").hasRole("ADMIN")
                .requestMatchers("/api/cluster/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .httpBasic(httpBasic -> {});
        
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // Use environment variables or configuration properties
        String username = adminUsername;
        String password = adminPassword;
        
        // Warn if using defaults in production-like environment
        if ("admin".equals(username) && "admin".equals(password)) {
            String env = System.getenv("SPRING_PROFILES_ACTIVE");
            if (env != null && (env.contains("prod") || env.contains("production"))) {
                logger.error("WARNING: Using default admin credentials in production! Set CACHE_ADMIN_USERNAME and CACHE_ADMIN_PASSWORD environment variables.");
            } else {
                logger.warn("Using default admin credentials. Set CACHE_ADMIN_USERNAME and CACHE_ADMIN_PASSWORD for production.");
            }
        }
        
        UserDetails admin = User.builder()
            .username(username)
            .password(passwordEncoder().encode(password))
            .roles("ADMIN")
            .build();
        
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

