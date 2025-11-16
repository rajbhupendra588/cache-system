# Automation Testing Quick Start Guide

## 🚀 Quick Start

### 1. Start the Application

```bash
mvn spring-boot:run
```

### 2. Run All Tests (in another terminal)

```bash
./src/test/scripts/run-automation-tests.sh all
```

### 3. View Screenshots

```bash
./src/test/scripts/run-automation-tests.sh screenshots
```

## 📋 Test Summary

- **Total Test Cases**: 27+
  - API Tests: 13
  - UI Tests: 10
  - Integration Tests: 5

- **Screenshots**: Automatically captured for all UI tests
- **Location**: `test-output/screenshots/`

## 🎯 Common Commands

```bash
# Run specific test categories
./src/test/scripts/run-automation-tests.sh api          # API tests only
./src/test/scripts/run-automation-tests.sh ui           # UI tests only
./src/test/scripts/run-automation-tests.sh integration  # Integration tests only

# Run with visible browser (for debugging)
TEST_HEADLESS=false ./src/test/scripts/run-automation-tests.sh ui

# View help
./src/test/scripts/run-automation-tests.sh help
```

## 📸 Screenshot Generation

Screenshots are **automatically captured** during UI test execution:

1. Run UI tests: `./src/test/scripts/run-automation-tests.sh ui`
2. Screenshots saved to: `test-output/screenshots/`
3. View screenshots: `./src/test/scripts/run-automation-tests.sh screenshots`

Each UI test captures multiple screenshots at key interaction points.

## 📚 Full Documentation

See [AUTOMATION_TESTING.md](./AUTOMATION_TESTING.md) for complete documentation.

