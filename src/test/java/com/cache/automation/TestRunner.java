package com.cache.automation;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test runner for executing all automation tests.
 * This can be used to run all tests programmatically and generate reports.
 */
public class TestRunner {
    private static final Logger logger = LoggerFactory.getLogger(TestRunner.class);

    public static void main(String[] args) {
        logger.info("Starting automation test execution...");
        
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                DiscoverySelectors.selectPackage("com.cache.automation")
            )
            .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        
        logger.info("Test execution completed:");
        logger.info("Tests found: {}", summary.getTestsFoundCount());
        logger.info("Tests started: {}", summary.getTestsStartedCount());
        logger.info("Tests succeeded: {}", summary.getTestsSucceededCount());
        logger.info("Tests failed: {}", summary.getTestsFailedCount());
        logger.info("Tests skipped: {}", summary.getTestsSkippedCount());
        
        if (summary.getTestsFailedCount() > 0) {
            logger.error("Some tests failed!");
            summary.getFailures().forEach(failure -> {
                logger.error("Failed test: {}", failure.getTestIdentifier().getDisplayName());
                logger.error("Exception: {}", failure.getException().getMessage());
            });
            System.exit(1);
        } else {
            logger.info("All tests passed!");
            System.exit(0);
        }
    }
}

