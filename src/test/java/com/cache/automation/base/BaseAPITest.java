package com.cache.automation.base;

import com.cache.automation.util.TestConfig;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for API automation tests.
 * Provides common setup for REST Assured and authentication.
 */
public abstract class BaseAPITest {
    protected static final Logger logger = LoggerFactory.getLogger(BaseAPITest.class);
    protected static RequestSpecification authenticatedRequestSpec;

    @BeforeAll
    public static void setUp() {
        RestAssured.baseURI = TestConfig.BASE_URL;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        // Create authenticated request specification
        authenticatedRequestSpec = new RequestSpecBuilder()
            .setBaseUri(TestConfig.BASE_URL)
            .setAuth(RestAssured.preemptive().basic(TestConfig.ADMIN_USERNAME, TestConfig.ADMIN_PASSWORD))
            .setContentType(ContentType.JSON)
            .build();
        
        logger.info("API test setup completed. Base URI: {}", TestConfig.BASE_URL);
    }

    /**
     * Gets an authenticated request specification.
     * 
     * @return RequestSpecification with authentication
     */
    protected RequestSpecification getAuthenticatedRequest() {
        return authenticatedRequestSpec;
    }
}

