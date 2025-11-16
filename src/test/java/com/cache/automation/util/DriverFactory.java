package com.cache.automation.util;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Utility class for managing WebDriver instances.
 */
public class DriverFactory {
    private static final Logger logger = LoggerFactory.getLogger(DriverFactory.class);
    private static final String BROWSER_PROPERTY = "test.browser";
    private static final String DEFAULT_BROWSER = "chrome";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Creates and configures a WebDriver instance based on system properties.
     * 
     * @return Configured WebDriver instance
     */
    public static WebDriver createDriver() {
        String browser = System.getProperty(BROWSER_PROPERTY, DEFAULT_BROWSER).toLowerCase();
        WebDriver driver;

        switch (browser) {
            case "chrome":
                WebDriverManager.chromedriver().setup();
                ChromeOptions chromeOptions = new ChromeOptions();
                chromeOptions.addArguments("--headless=new");
                chromeOptions.addArguments("--no-sandbox");
                chromeOptions.addArguments("--disable-dev-shm-usage");
                chromeOptions.addArguments("--disable-gpu");
                chromeOptions.addArguments("--window-size=1920,1080");
                driver = new ChromeDriver(chromeOptions);
                break;
                
            case "firefox":
                WebDriverManager.firefoxdriver().setup();
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                firefoxOptions.addArguments("--headless");
                driver = new FirefoxDriver(firefoxOptions);
                break;
                
            case "edge":
                WebDriverManager.edgedriver().setup();
                EdgeOptions edgeOptions = new EdgeOptions();
                edgeOptions.addArguments("--headless");
                driver = new EdgeDriver(edgeOptions);
                break;
                
            default:
                logger.warn("Unknown browser: {}. Using Chrome.", browser);
                WebDriverManager.chromedriver().setup();
                ChromeOptions defaultOptions = new ChromeOptions();
                defaultOptions.addArguments("--headless=new");
                defaultOptions.addArguments("--no-sandbox");
                defaultOptions.addArguments("--disable-dev-shm-usage");
                driver = new ChromeDriver(defaultOptions);
        }

        // Configure timeouts
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
        driver.manage().window().maximize();

        logger.info("WebDriver created: {}", browser);
        return driver;
    }

    /**
     * Quits the WebDriver instance and releases resources.
     * 
     * @param driver WebDriver instance to quit
     */
    public static void quitDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
                logger.info("WebDriver quit successfully");
            } catch (Exception e) {
                logger.error("Error quitting WebDriver", e);
            }
        }
    }
}

