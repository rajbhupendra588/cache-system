package com.cache.automation.util;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility for capturing PNG screenshots of multi-node cluster dashboards.
 * Can be called from shell scripts or Java tests.
 */
public class MultiNodeScreenshotUtil {
    private static final Logger logger = LoggerFactory.getLogger(MultiNodeScreenshotUtil.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Captures a PNG screenshot of a node's dashboard.
     * 
     * @param nodeNum Node number (1-10)
     * @param httpPort HTTP port of the node
     * @param description Description of the screenshot
     * @param outputDir Output directory for screenshots
     * @return Path to the saved screenshot file
     */
    public static String captureNodeScreenshot(int nodeNum, int httpPort, String description, String outputDir) {
        WebDriver driver = null;
        try {
            // Setup WebDriver
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            driver = new ChromeDriver(options);

            // Navigate to dashboard
            String url = String.format("http://localhost:%d/", httpPort);
            logger.info("Capturing screenshot from: {}", url);
            driver.get(url);

            // Wait for page to load
            Thread.sleep(2000);

            // Create output directory if it doesn't exist
            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            // Capture screenshot
            String timestamp = LocalDateTime.now().format(FORMATTER);
            String safeDescription = sanitizeFileName(description);
            String filename = String.format("node%d_%s_%s.png", nodeNum, safeDescription, timestamp);
            Path filePath = dir.resolve(filename);

            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), filePath);

            logger.info("Screenshot captured: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();

        } catch (Exception e) {
            logger.error("Failed to capture screenshot for node {}: {}", nodeNum, e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("Error closing driver: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Captures cluster status page screenshot.
     * 
     * @param nodeNum Node number
     * @param httpPort HTTP port
     * @param description Description
     * @param outputDir Output directory
     * @return Path to screenshot
     */
    public static String captureClusterStatusScreenshot(int nodeNum, int httpPort, String description, String outputDir) {
        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--window-size=1920,1080");
            driver = new ChromeDriver(options);

            // Navigate to cluster API endpoint (we'll create a simple HTML view)
            // For now, navigate to dashboard and then cluster view
            String url = String.format("http://localhost:%d/#cluster", httpPort);
            logger.info("Capturing cluster status screenshot from: {}", url);
            driver.get(url);

            Thread.sleep(3000); // Wait for cluster view to load

            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String timestamp = LocalDateTime.now().format(FORMATTER);
            String safeDescription = sanitizeFileName(description);
            String filename = String.format("node%d_%s_%s.png", nodeNum, safeDescription, timestamp);
            Path filePath = dir.resolve(filename);

            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), filePath);

            logger.info("Cluster status screenshot captured: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();

        } catch (Exception e) {
            logger.error("Failed to capture cluster status screenshot: {}", e.getMessage());
            return null;
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logger.warn("Error closing driver: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Main method for command-line usage.
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: MultiNodeScreenshotUtil <nodeNum> <httpPort> <description> <outputDir>");
            System.exit(1);
        }

        int nodeNum = Integer.parseInt(args[0]);
        int httpPort = Integer.parseInt(args[1]);
        String description = args[2];
        String outputDir = args[3];

        String result = captureNodeScreenshot(nodeNum, httpPort, description, outputDir);
        if (result != null) {
            System.out.println(result);
            System.exit(0);
        } else {
            System.err.println("Failed to capture screenshot");
            System.exit(1);
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_")
                   .replaceAll("_{2,}", "_")
                   .toLowerCase();
    }
}

