# Screenshot Format Explanation

## Why PNG Instead of HTML?

The multi-node test script was initially creating **HTML files** with embedded JSON data instead of actual image screenshots. This has been **updated** to capture **PNG image screenshots** using Selenium WebDriver, consistent with the UI automation tests.

## Changes Made

### Before (HTML Files)
- Created HTML files with JSON data embedded
- Not actual visual screenshots
- Harder to review visually
- Format: `.html` files

### After (PNG Screenshots)
- Captures actual PNG image screenshots
- Uses Selenium WebDriver (headless Chrome)
- Visual representation of the dashboard
- Format: `.png` files (standard image format)
- Consistent with UI automation tests

## Implementation

### New Utility Class
Created `MultiNodeScreenshotUtil.java` that:
- Uses Selenium WebDriver to capture screenshots
- Navigates to each node's dashboard
- Captures PNG screenshots
- Can be called from shell scripts or Java tests

### Updated Script
The `run-multi-node-10-test.sh` script now:
- Calls the Java utility to capture PNG screenshots
- Falls back to text data capture if screenshot fails
- Generates actual image files instead of HTML

## Screenshot Location

Screenshots are saved in:
```
test-output/screenshots/multi-node-10/
```

## File Naming

PNG screenshots follow this format:
```
node{N}_{description}_{timestamp}.png
```

Examples:
- `node1_initial_state_2025-11-16_19-19-39.png`
- `node5_cluster_status_2025-11-16_19-19-45.png`
- `node10_final_state_2025-11-16_19-20-15.png`

## Benefits of PNG Format

1. **Visual Review**: Actual screenshots of the dashboard
2. **Consistency**: Same format as UI automation tests
3. **Standard Format**: PNG is universally supported
4. **Better Documentation**: Visual proof of test execution
5. **Easier Sharing**: Can be included in reports easily

## Migration

Old HTML files can be kept for reference, but new test runs will generate PNG screenshots. The script automatically handles the conversion.

