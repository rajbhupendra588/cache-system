package com.cache.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) configuration for API documentation.
 * 
 * <p>This configuration sets up Swagger UI for interactive API documentation
 * and testing. The API documentation is available at:
 * <ul>
 *   <li>Swagger UI: <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a></li>
 *   <li>OpenAPI JSON: <a href="http://localhost:8080/v3/api-docs">/v3/api-docs</a></li>
 * </ul>
 * 
 * @author Cache System
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures OpenAPI documentation with API information and security.
     * 
     * @return OpenAPI configuration
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Distributed Cache Management System API")
                .version("1.0.0")
                .description("""
                    RESTful API for managing distributed cache operations.
                    
                    ## Features
                    - Cache management (get, put, invalidate)
                    - Cache statistics and monitoring
                    - Cluster coordination
                    - Key browsing and search
                    
                    ## Authentication
                    All API endpoints require Basic Authentication with admin credentials.
                    Use the "Authorize" button above to set your credentials.
                    """)
                .contact(new Contact()
                    .name("Cache System Support")
                    .email("support@cache-system.local"))
                .license(new License()
                    .name("Internal Use")
                    .url("https://cache-system.local")))
            .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
            .components(new Components()
                .addSecuritySchemes("basicAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")
                    .description("Basic authentication with admin credentials")));
    }
}

