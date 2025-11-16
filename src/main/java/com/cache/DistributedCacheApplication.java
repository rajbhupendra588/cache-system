package com.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DistributedCacheApplication {
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheApplication.class);

    public static void main(String[] args) {
        // Log startup information
        logger.info("Starting Distributed Cache Management System...");
        logger.info("Java version: {}", System.getProperty("java.version"));
        logger.info("Spring Boot version: {}", org.springframework.boot.SpringBootVersion.getVersion());
        
        SpringApplication.run(DistributedCacheApplication.class, args);
        
        logger.info("Distributed Cache Management System started successfully");
    }
}

