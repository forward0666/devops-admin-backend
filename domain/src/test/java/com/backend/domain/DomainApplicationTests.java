package com.backend.domain;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@ActiveProfiles("test")
class DomainApplicationTests {

    @Test
    void contextLoads() {
        assertDoesNotThrow(() -> {});
    }
}
