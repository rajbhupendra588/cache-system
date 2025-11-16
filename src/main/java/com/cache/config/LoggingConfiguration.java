package com.cache.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter to add correlation IDs to requests for better traceability.
 */
@Component
public class LoggingConfiguration implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            // Get or create correlation ID
            String correlationId = httpRequest.getHeader("X-Correlation-ID");
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            MDC.put("correlationId", correlationId);
            MDC.put("requestId", UUID.randomUUID().toString());
            
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

