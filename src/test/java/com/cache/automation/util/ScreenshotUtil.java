package com.cache.automation.util;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for capturing screenshots during test execution.
 */
public class ScreenshotUtil {
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotUtil.class);
    private static final String SCREENSHOT_DIR = "test-output/screenshots";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    static {
        // Create screenshot directory if it doesn't exist
        try {
            Path dir = Paths.get(SCREENSHOT_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            logger.error("Failed to create screenshot directory", e);
        }
    }

    /**
     * Captures a screenshot from the WebDriver.
     * 
     * @param driver WebDriver instance
     * @param testName Name of the test case
     * @param description Description of what the screenshot captures
     * @return Path to the saved screenshot file
     */
    public static String captureScreenshot(WebDriver driver, String testName, String description) {
        try {
            if (driver instanceof TakesScreenshot) {
                String timestamp = LocalDateTime.now().format(FORMATTER);
                String safeTestName = sanitizeFileName(testName);
                String safeDescription = sanitizeFileName(description);
                String fileName = String.format("%s_%s_%s.png", safeTestName, safeDescription, timestamp);
                Path filePath = Paths.get(SCREENSHOT_DIR, fileName);
                
                File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(screenshot.toPath(), filePath);
                
                logger.info("Screenshot captured: {}", filePath.toAbsolutePath());
                return filePath.toAbsolutePath().toString();
            } else {
                logger.warn("WebDriver does not support screenshots");
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to capture screenshot", e);
            return null;
        }
    }

    /**
     * Captures a screenshot with a default description.
     * 
     * @param driver WebDriver instance
     * @param testName Name of the test case
     * @return Path to the saved screenshot file
     */
    public static String captureScreenshot(WebDriver driver, String testName) {
        return captureScreenshot(driver, testName, "screenshot");
    }

    /**
     * Sanitizes a string to be used as a filename.
     * 
     * @param name Original name
     * @return Sanitized name safe for use in filenames
     */
    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_")
                   .replaceAll("_{2,}", "_")
                   .toLowerCase();
    }

    /**
     * Gets the screenshot directory path.
     * 
     * @return Path to screenshot directory
     */
    public static String getScreenshotDirectory() {
        return SCREENSHOT_DIR;
    }
}

