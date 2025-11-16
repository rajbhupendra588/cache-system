package com.cache.automation.util;

/**
 * Configuration class for test settings.
 */
public class TestConfig {
    public static final String BASE_URL = System.getProperty("test.base.url", "http://localhost:8080");
    public static final String ADMIN_USERNAME = System.getProperty("test.admin.username", "admin");
    public static final String ADMIN_PASSWORD = System.getProperty("test.admin.password", "admin");
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final boolean HEADLESS_MODE = Boolean.parseBoolean(System.getProperty("test.headless", "true"));
    
    private TestConfig() {
        // Utility class
    }
}

