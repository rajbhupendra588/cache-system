# Automation Testing Documentation

## Overview

This document provides comprehensive documentation for the automation testing framework implemented for the Distributed Cache Management System. The framework includes API testing, UI testing, and integration testing with automatic screenshot capture for each test case.

## Table of Contents

1. [Test Framework Architecture](#test-framework-architecture)
2. [Test Structure](#test-structure)
3. [Prerequisites](#prerequisites)
4. [Running Tests](#running-tests)
5. [Test Cases](#test-cases)
6. [Screenshot Management](#screenshot-management)
7. [Test Reports](#test-reports)
8. [Troubleshooting](#troubleshooting)

## Test Framework Architecture

### Technology Stack

- **JUnit 5**: Test framework for Java
- **Selenium WebDriver**: UI automation testing
- **REST Assured**: API testing
- **WebDriverManager**: Automatic browser driver management
- **SLF4J**: Logging framework

### Test Categories

1. **API Tests**: Test REST API endpoints
2. **UI Tests**: Test web dashboard functionality
3. **Integration Tests**: Test end-to-end workflows

## Test Structure

```
src/test/java/com/cache/automation/
├── api/
│   ├── CacheControllerAPITest.java      # Cache API tests
│   └── ClusterControllerAPITest.java   # Cluster API tests
├── ui/
│   └── DashboardUITest.java             # Dashboard UI tests
├── integration/
│   └── CacheIntegrationTest.java       # Integration tests
├── base/
│   ├── BaseAPITest.java                # Base class for API tests
│   └── BaseUITest.java                 # Base class for UI tests
├── util/
│   ├── DriverFactory.java              # WebDriver management
│   ├── ScreenshotUtil.java             # Screenshot capture
│   └── TestConfig.java                 # Test configuration
└── TestRunner.java                     # Test execution runner
```

## Prerequisites

### Required Software

1. **Java 17+**: Required for running tests
2. **Maven 3.6+**: Build and dependency management
3. **Chrome/Firefox/Edge**: Browser for UI tests (WebDriverManager handles driver installation)
4. **Application Running**: The cache system must be running on `http://localhost:8080` (or configured URL)

### Configuration

Test configuration can be customized via system properties:

```bash
# Base URL for the application
-Dtest.base.url=http://localhost:8080

# Admin credentials
-Dtest.admin.username=admin
-Dtest.admin.password=admin

# Browser selection (chrome, firefox, edge)
-Dtest.browser=chrome

# Headless mode (true/false)
-Dtest.headless=true
```

## Running Tests

### Quick Start with Test Script

The easiest way to run tests is using the provided script:

```bash
# Run all tests
./src/test/scripts/run-automation-tests.sh all

# Run specific test categories
./src/test/scripts/run-automation-tests.sh api
./src/test/scripts/run-automation-tests.sh ui
./src/test/scripts/run-automation-tests.sh integration

# View screenshots
./src/test/scripts/run-automation-tests.sh screenshots

# Run with visible browser (for debugging)
TEST_HEADLESS=false ./src/test/scripts/run-automation-tests.sh ui
```

### Run All Tests with Maven

```bash
mvn test
```

### Run Specific Test Class

```bash
# API Tests
mvn test -Dtest=CacheControllerAPITest
mvn test -Dtest=ClusterControllerAPITest

# UI Tests
mvn test -Dtest=DashboardUITest

# Integration Tests
mvn test -Dtest=CacheIntegrationTest
```

### Run Tests with Custom Configuration

```bash
mvn test -Dtest.base.url=http://localhost:8080 \
        -Dtest.admin.username=admin \
        -Dtest.admin.password=admin \
        -Dtest.browser=chrome \
        -Dtest.headless=false
```

### Run Tests Programmatically

```bash
mvn test-compile exec:java -Dexec.mainClass="com.cache.automation.TestRunner"
```

## Test Cases

### API Test Cases

#### Cache Controller API Tests

| Test ID | Test Case | Description |
|---------|-----------|-------------|
| TC-API-001 | List all caches | Verify listing all configured caches with statistics |
| TC-API-002 | Get cache details | Verify retrieving detailed information about a specific cache |
| TC-API-003 | Get cache statistics | Verify retrieving cache statistics (hits, misses, size) |
| TC-API-004 | Put value into cache | Verify cache put operation (via programmatic API) |
| TC-API-005 | Get cache value by key | Verify retrieving a cached value by key |
| TC-API-006 | List cache keys | Verify listing all keys in a cache with pagination |
| TC-API-007 | List cache keys with prefix filter | Verify filtering keys by prefix |
| TC-API-008 | Invalidate single cache key | Verify invalidating a single cache entry |
| TC-API-009 | Invalidate multiple cache keys | Verify invalidating multiple cache entries |
| TC-API-010 | Invalidate cache keys by prefix | Verify invalidating all keys matching a prefix |
| TC-API-011 | Clear entire cache | Verify clearing all entries from a cache |
| TC-API-012 | Test authentication failure | Verify proper handling of authentication failures |
| TC-API-013 | Test invalid cache name | Verify handling of non-existent cache names |

#### Cluster Controller API Tests

| Test ID | Test Case | Description |
|---------|-----------|-------------|
| TC-CLUSTER-001 | Get cluster status | Verify retrieving cluster status and node information |
| TC-CLUSTER-002 | List cluster nodes | Verify listing all nodes in the cluster |
| TC-CLUSTER-003 | Get node details | Verify retrieving detailed information about a specific node |
| TC-CLUSTER-004 | Test cluster authentication | Verify authentication for cluster endpoints |

### UI Test Cases

| Test ID | Test Case | Description |
|---------|-----------|-------------|
| TC-UI-001 | Load dashboard home page | Verify dashboard loads correctly |
| TC-UI-002 | Verify dashboard statistics display | Verify all statistics cards are displayed |
| TC-UI-003 | Navigate to Caches view | Verify navigation to caches view |
| TC-UI-004 | Navigate to Cluster view | Verify navigation to cluster view |
| TC-UI-005 | Navigate to Metrics view | Verify navigation to metrics view |
| TC-UI-006 | Test refresh dashboard button | Verify refresh functionality |
| TC-UI-007 | Verify sidebar navigation | Verify all navigation items work correctly |
| TC-UI-008 | Verify system status indicator | Verify system status is displayed |
| TC-UI-009 | Test cache list display | Verify cache list is displayed on dashboard |
| TC-UI-010 | Test cache browser functionality | Verify cache browser features |

### Integration Test Cases

| Test ID | Test Case | Description |
|---------|-----------|-------------|
| TC-INT-001 | Complete cache workflow | Test end-to-end cache operations (Put, Get, Invalidate) |
| TC-INT-002 | Test cache statistics tracking | Verify statistics are tracked correctly |
| TC-INT-003 | Test cache key listing and pagination | Verify key listing with pagination works |
| TC-INT-004 | Test cache clear operation | Verify clearing entire cache works |
| TC-INT-005 | Test multiple cache operations | Verify batch operations work correctly |

## Screenshot Management

### Automatic Screenshot Capture

**IMPORTANT**: Screenshots are automatically captured during test execution. To generate screenshots:

1. **Start the application**:
   ```bash
   mvn spring-boot:run
   ```

2. **Run UI tests** (in another terminal):
   ```bash
   ./src/test/scripts/run-automation-tests.sh ui
   # OR
   mvn test -Dtest=DashboardUITest
   ```

3. **Screenshots will be automatically saved** to `test-output/screenshots/`

The test framework automatically captures screenshots for:

1. **UI Tests**: Screenshots are captured at key points:
   - After page load
   - Before and after user interactions
   - On test completion (final state)
   - On test failures

2. **Screenshot Naming Convention**

   Format: `{TestClassName}_{TestMethodName}_{Description}_{Timestamp}.png`

   Examples:
   - `DashboardUITest_testLoadDashboardHomePage_dashboard_home_loaded_2024-01-15_14-30-45.png`
   - `DashboardUITest_testNavigateToCachesView_caches_view_2024-01-15_14-31-12.png`

3. **Screenshot Location**

   All screenshots are saved in: `test-output/screenshots/`

### Screenshot Capture Points

#### UI Tests Screenshots

| Test Case | Screenshot Description | When Captured |
|-----------|------------------------|---------------|
| TC-UI-001 | `dashboard_home_loaded` | After dashboard page loads |
| TC-UI-002 | `dashboard_statistics` | After statistics are displayed |
| TC-UI-003 | `caches_view` | After navigating to caches view |
| TC-UI-004 | `cluster_view` | After navigating to cluster view |
| TC-UI-005 | `metrics_view` | After navigating to metrics view |
| TC-UI-006 | `before_refresh`, `after_refresh` | Before and after refresh |
| TC-UI-007 | `sidebar_navigation` | After sidebar is loaded |
| TC-UI-008 | `status_indicator` | After status indicator is displayed |
| TC-UI-009 | `cache_list` | After cache list is displayed |
| TC-UI-010 | `cache_browser_initial` | After cache browser is loaded |

### Viewing Screenshots

After test execution, screenshots can be found in:

```bash
test-output/screenshots/
```

**Quick way to view screenshots**:

```bash
# Using the test script (automatically opens directory)
./src/test/scripts/run-automation-tests.sh screenshots
```

**Manual viewing**:

```bash
# On macOS/Linux
open test-output/screenshots/

# On Windows
start test-output/screenshots/
```

### Screenshot Gallery

Each test execution generates screenshots for all UI test cases. Here's what you'll find:

#### Dashboard View Screenshots
- `DashboardUITest_testLoadDashboardHomePage_dashboard_home_loaded_*.png` - Initial dashboard load
- `DashboardUITest_testDashboardStatisticsDisplay_dashboard_statistics_*.png` - Statistics display

#### Navigation Screenshots
- `DashboardUITest_testNavigateToCachesView_caches_view_*.png` - Caches view
- `DashboardUITest_testNavigateToClusterView_cluster_view_*.png` - Cluster view
- `DashboardUITest_testNavigateToMetricsView_metrics_view_*.png` - Metrics view

#### Interaction Screenshots
- `DashboardUITest_testRefreshDashboardButton_before_refresh_*.png` - Before refresh
- `DashboardUITest_testRefreshDashboardButton_after_refresh_*.png` - After refresh

#### Component Screenshots
- `DashboardUITest_testSidebarNavigation_sidebar_navigation_*.png` - Sidebar
- `DashboardUITest_testSystemStatusIndicator_status_indicator_*.png` - Status indicator
- `DashboardUITest_testCacheListDisplay_cache_list_*.png` - Cache list
- `DashboardUITest_testCacheBrowserFunctionality_cache_browser_initial_*.png` - Cache browser

**Note**: Screenshots are timestamped, so each test run creates new screenshots. Old screenshots are not automatically deleted.

## Test Reports

### Maven Surefire Reports

After running tests, Maven generates test reports in:

```
target/surefire-reports/
```

View the HTML report:

```bash
open target/site/surefire-report.html
```

### Test Execution Summary

The test runner provides a summary:

```
Test execution completed:
Tests found: 27
Tests started: 27
Tests succeeded: 27
Tests failed: 0
Tests skipped: 0
```

## Test Execution Flow

### API Test Execution Flow

1. **Setup**: Initialize REST Assured with base URL and authentication
2. **Test Execution**: Execute API calls and verify responses
3. **Assertions**: Validate response status, body, and data
4. **Teardown**: Clean up resources

### UI Test Execution Flow

1. **Setup**: Initialize WebDriver and navigate to base URL
2. **Test Execution**: 
   - Navigate to pages
   - Interact with elements
   - Capture screenshots at key points
3. **Assertions**: Verify UI elements and behavior
4. **Teardown**: Capture final screenshot and quit WebDriver

## Best Practices

### Writing New Tests

1. **Extend Base Classes**: Use `BaseAPITest` or `BaseUITest`
2. **Use Descriptive Names**: Test methods should clearly describe what they test
3. **Add Screenshots**: Use `takeScreenshot()` for UI tests at key points
4. **Order Tests**: Use `@Order` annotation for dependent tests
5. **Add Logging**: Use logger for test execution tracking

### Example: Adding a New UI Test

```java
@Test
@Order(11)
@DisplayName("TC-UI-011: Test new feature")
public void testNewFeature() {
    logger.info("Executing TC-UI-011: Test new feature");
    
    navigateTo("/new-feature");
    takeScreenshot("new_feature_loaded");
    
    // Test implementation
    WebElement element = driver.findElement(By.id("element-id"));
    assertNotNull(element);
    
    logger.info("New feature test completed");
}
```

## Troubleshooting

### Common Issues

#### 1. Tests Fail with Connection Refused

**Problem**: Application is not running

**Solution**: 
```bash
# Start the application first
mvn spring-boot:run
# Then run tests in another terminal
mvn test
```

#### 2. WebDriver Issues

**Problem**: Browser driver not found

**Solution**: WebDriverManager automatically downloads drivers. Ensure internet connection is available.

#### 3. Screenshots Not Captured

**Problem**: Screenshots directory not created

**Solution**: The directory is created automatically. Check permissions:
```bash
chmod -R 755 test-output/
```

#### 4. Authentication Failures

**Problem**: Tests fail with 401 Unauthorized

**Solution**: Verify credentials in `TestConfig.java` or pass via system properties:
```bash
mvn test -Dtest.admin.username=admin -Dtest.admin.password=admin
```

#### 5. Headless Mode Issues

**Problem**: UI tests fail in headless mode

**Solution**: Run with headless disabled for debugging:
```bash
mvn test -Dtest.headless=false
```

## Continuous Integration

### CI/CD Integration

The tests can be integrated into CI/CD pipelines:

```yaml
# Example GitHub Actions
- name: Run Tests
  run: |
    mvn clean test
    # Archive screenshots
    tar -czf test-screenshots.tar.gz test-output/screenshots/
```

### Test Reports in CI

Configure CI to publish test reports and screenshots as artifacts.

## Maintenance

### Updating Tests

1. **When API Changes**: Update corresponding API test methods
2. **When UI Changes**: Update UI test selectors and interactions
3. **When Features Added**: Add new test cases following the naming convention

### Test Data Management

- Use unique test keys with timestamps to avoid conflicts
- Clean up test data after tests complete
- Use test-specific cache names when possible

## Conclusion

This automation testing framework provides comprehensive coverage of the Distributed Cache Management System with:

- ✅ **27+ Test Cases** covering API, UI, and integration scenarios
- ✅ **Automatic Screenshot Capture** for all UI tests
- ✅ **Comprehensive Logging** for debugging and monitoring
- ✅ **CI/CD Ready** for continuous integration
- ✅ **Extensible Framework** for adding new tests

For questions or issues, refer to the test source code or contact the development team.

---

**Last Updated**: November 16, 2025  
**Test Framework Version**: 1.0.0  
**Test Coverage**: API (13), UI (10), Integration (5)

