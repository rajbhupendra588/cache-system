package com.cache.automation.ui;

import com.cache.automation.base.BaseUITest;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI automation tests for the Cache Management Dashboard.
 * Tests all dashboard views and interactions with screenshot capture.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DashboardUITest extends BaseUITest {
    private static final Logger logger = LoggerFactory.getLogger(DashboardUITest.class);

    @Test
    @Order(1)
    @DisplayName("TC-UI-001: Load dashboard home page")
    public void testLoadDashboardHomePage() {
        logger.info("Executing TC-UI-001: Load dashboard home page");
        
        navigateTo("/");
        takeScreenshot("dashboard_home_loaded");
        
        // Wait for page to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("app-container")));
        
        // Verify main elements are present
        WebElement sidebar = driver.findElement(By.className("sidebar"));
        assertNotNull(sidebar, "Sidebar should be present");
        
        WebElement mainContent = driver.findElement(By.className("main-content"));
        assertNotNull(mainContent, "Main content should be present");
        
        logger.info("Dashboard home page loaded successfully");
    }

    @Test
    @Order(2)
    @DisplayName("TC-UI-002: Verify dashboard statistics display")
    public void testDashboardStatisticsDisplay() {
        logger.info("Executing TC-UI-002: Verify dashboard statistics display");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("stats-grid")));
        takeScreenshot("dashboard_statistics");
        
        // Verify stat cards are present
        List<WebElement> statCards = driver.findElements(By.className("stat-card"));
        assertTrue(statCards.size() >= 4, "Should have at least 4 stat cards");
        
        // Verify specific stat cards
        WebElement totalCaches = driver.findElement(By.id("total-caches"));
        assertNotNull(totalCaches, "Total caches stat should be present");
        
        WebElement totalEntries = driver.findElement(By.id("total-entries"));
        assertNotNull(totalEntries, "Total entries stat should be present");
        
        WebElement avgHitRatio = driver.findElement(By.id("avg-hit-ratio"));
        assertNotNull(avgHitRatio, "Average hit ratio stat should be present");
        
        WebElement totalMemory = driver.findElement(By.id("total-memory"));
        assertNotNull(totalMemory, "Total memory stat should be present");
        
        logger.info("Dashboard statistics displayed correctly");
    }

    @Test
    @Order(3)
    @DisplayName("TC-UI-003: Navigate to Caches view")
    public void testNavigateToCachesView() {
        logger.info("Executing TC-UI-003: Navigate to Caches view");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("nav-item")));
        
        // Click on Caches navigation item
        WebElement cachesNav = driver.findElement(By.xpath("//a[@data-view='caches']"));
        cachesNav.click();
        
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("caches-view")));
        takeScreenshot("caches_view");
        
        // Verify caches view is active
        WebElement cachesView = driver.findElement(By.id("caches-view"));
        assertTrue(cachesView.getAttribute("class").contains("active"), 
            "Caches view should be active");
        
        // Verify cache selector is present
        WebElement cacheSelector = driver.findElement(By.id("cache-selector"));
        assertNotNull(cacheSelector, "Cache selector should be present");
        
        logger.info("Successfully navigated to Caches view");
    }

    @Test
    @Order(4)
    @DisplayName("TC-UI-004: Navigate to Cluster view")
    public void testNavigateToClusterView() {
        logger.info("Executing TC-UI-004: Navigate to Cluster view");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("nav-item")));
        
        // Click on Cluster navigation item
        WebElement clusterNav = driver.findElement(By.xpath("//a[@data-view='cluster']"));
        clusterNav.click();
        
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cluster-view")));
        takeScreenshot("cluster_view");
        
        // Verify cluster view is active
        WebElement clusterView = driver.findElement(By.id("cluster-view"));
        assertTrue(clusterView.getAttribute("class").contains("active"), 
            "Cluster view should be active");
        
        logger.info("Successfully navigated to Cluster view");
    }

    @Test
    @Order(5)
    @DisplayName("TC-UI-005: Navigate to Metrics view")
    public void testNavigateToMetricsView() {
        logger.info("Executing TC-UI-005: Navigate to Metrics view");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("nav-item")));
        
        // Click on Metrics navigation item
        WebElement metricsNav = driver.findElement(By.xpath("//a[@data-view='metrics']"));
        metricsNav.click();
        
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("metrics-view")));
        takeScreenshot("metrics_view");
        
        // Verify metrics view is active
        WebElement metricsView = driver.findElement(By.id("metrics-view"));
        assertTrue(metricsView.getAttribute("class").contains("active"), 
            "Metrics view should be active");
        
        logger.info("Successfully navigated to Metrics view");
    }

    @Test
    @Order(6)
    @DisplayName("TC-UI-006: Test refresh dashboard button")
    public void testRefreshDashboardButton() {
        logger.info("Executing TC-UI-006: Test refresh dashboard button");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("btn-refresh")));
        takeScreenshot("before_refresh");
        
        // Click refresh button
        WebElement refreshButton = driver.findElement(By.className("btn-refresh"));
        refreshButton.click();
        
        // Wait a moment for refresh to complete
        waitFor(2000);
        takeScreenshot("after_refresh");
        
        logger.info("Dashboard refresh button clicked successfully");
    }

    @Test
    @Order(7)
    @DisplayName("TC-UI-007: Verify sidebar navigation")
    public void testSidebarNavigation() {
        logger.info("Executing TC-UI-007: Verify sidebar navigation");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("sidebar-nav")));
        takeScreenshot("sidebar_navigation");
        
        // Verify all navigation items are present
        List<WebElement> navItems = driver.findElements(By.className("nav-item"));
        assertTrue(navItems.size() >= 4, "Should have at least 4 navigation items");
        
        // Verify each navigation item
        String[] expectedViews = {"dashboard", "caches", "cluster", "metrics"};
        for (String view : expectedViews) {
            WebElement navItem = driver.findElement(By.xpath(
                String.format("//a[@data-view='%s']", view)));
            assertNotNull(navItem, "Navigation item for " + view + " should be present");
        }
        
        logger.info("Sidebar navigation verified");
    }

    @Test
    @Order(8)
    @DisplayName("TC-UI-008: Verify system status indicator")
    public void testSystemStatusIndicator() {
        logger.info("Executing TC-UI-008: Verify system status indicator");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("status-indicator")));
        takeScreenshot("status_indicator");
        
        // Verify status indicator is present
        WebElement statusIndicator = driver.findElement(By.className("status-indicator"));
        assertNotNull(statusIndicator, "Status indicator should be present");
        
        // Verify status dot
        WebElement statusDot = driver.findElement(By.className("status-dot"));
        assertNotNull(statusDot, "Status dot should be present");
        
        logger.info("System status indicator verified");
    }

    @Test
    @Order(9)
    @DisplayName("TC-UI-009: Test cache list display")
    public void testCacheListDisplay() {
        logger.info("Executing TC-UI-009: Test cache list display");
        
        navigateTo("/");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cache-list")));
        takeScreenshot("cache_list");
        
        // Verify cache list container is present
        WebElement cacheList = driver.findElement(By.id("cache-list"));
        assertNotNull(cacheList, "Cache list should be present");
        
        logger.info("Cache list display verified");
    }

    @Test
    @Order(10)
    @DisplayName("TC-UI-010: Test cache browser functionality")
    public void testCacheBrowserFunctionality() {
        logger.info("Executing TC-UI-010: Test cache browser functionality");
        
        navigateTo("/");
        
        // Navigate to caches view
        WebElement cachesNav = driver.findElement(By.xpath("//a[@data-view='caches']"));
        cachesNav.click();
        
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("caches-view")));
        takeScreenshot("cache_browser_initial");
        
        // Verify cache browser elements
        WebElement cacheSelector = driver.findElement(By.id("cache-selector"));
        assertNotNull(cacheSelector, "Cache selector should be present");
        
        WebElement loadKeysButton = driver.findElement(
            By.xpath("//button[contains(text(), 'Load Keys')]"));
        assertNotNull(loadKeysButton, "Load Keys button should be present");
        
        WebElement clearCacheButton = driver.findElement(
            By.xpath("//button[contains(text(), 'Clear Cache')]"));
        assertNotNull(clearCacheButton, "Clear Cache button should be present");
        
        logger.info("Cache browser functionality verified");
    }
}

