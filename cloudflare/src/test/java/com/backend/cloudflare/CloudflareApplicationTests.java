package com.backend.cloudflare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Sanity check: Application context loads without errors.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class CloudflareApplicationTests {

    @Test
    void contextLoads() {
    }
}