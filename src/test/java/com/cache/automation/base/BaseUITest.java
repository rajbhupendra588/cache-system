package com.cache.automation.base;

import com.cache.automation.util.ScreenshotUtil;
import com.cache.automation.util.TestConfig;
import com.cache.automation.util.DriverFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Base class for UI automation tests.
 * Provides common setup, teardown, and utility methods.
 */
public abstract class BaseUITest {
    protected static final Logger logger = LoggerFactory.getLogger(BaseUITest.class);
    protected WebDriver driver;
    protected WebDriverWait wait;
    protected String baseUrl;

    @BeforeEach
    public void setUp() {
        baseUrl = TestConfig.BASE_URL;
        driver = DriverFactory.createDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(TestConfig.DEFAULT_TIMEOUT_SECONDS));
        logger.info("Test setup completed. Base URL: {}", baseUrl);
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            try {
                // Capture final screenshot before quitting
                String testName = this.getClass().getSimpleName() + "_" + 
                    Thread.currentThread().getStackTrace()[2].getMethodName();
                ScreenshotUtil.captureScreenshot(driver, testName, "final_state");
            } catch (Exception e) {
                logger.error("Error capturing final screenshot", e);
            }
            DriverFactory.quitDriver(driver);
        }
        logger.info("Test teardown completed");
    }

    /**
     * Navigates to a specific URL.
     * 
     * @param path Path to navigate to (relative to base URL)
     */
    protected void navigateTo(String path) {
        String url = baseUrl + path;
        logger.info("Navigating to: {}", url);
        driver.get(url);
    }

    /**
     * Captures a screenshot with a description.
     * 
     * @param description Description of what the screenshot captures
     */
    protected void takeScreenshot(String description) {
        String testName = this.getClass().getSimpleName() + "_" + 
            Thread.currentThread().getStackTrace()[2].getMethodName();
        ScreenshotUtil.captureScreenshot(driver, testName, description);
    }

    /**
     * Waits for a specified amount of time.
     * 
     * @param milliseconds Time to wait in milliseconds
     */
    protected void waitFor(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Wait interrupted", e);
        }
    }
}

