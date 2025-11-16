package com.cache.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving the web UI dashboard.
 * 
 * <p>This controller serves the main dashboard page and delegates
 * static resource serving to Spring Boot's default static resource handler.
 * 
 * @author Cache System
 * @since 1.0.0
 */
@Controller
public class WebController {
    
    /**
     * Serves the main dashboard page.
     * 
     * @return the name of the view template (redirects to index.html)
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }
    
    /**
     * Serves the dashboard page directly.
     * 
     * @return the name of the view template
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/index.html";
    }
}

