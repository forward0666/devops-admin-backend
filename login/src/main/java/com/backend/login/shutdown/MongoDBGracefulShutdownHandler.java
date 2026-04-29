package com.backend.login.shutdown;

import com.mongodb.client.MongoClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MongoDBGracefulShutdownHandler {

    @Autowired(required = false)
    private MongoClient mongoClient;

    @PreDestroy
    public void shutdown() {
        if (mongoClient == null) return;

        log.info("Closing MongoDB client...");
        try {
            mongoClient.close();
        } catch (Exception e) {
            log.error("Error closing MongoDB client", e);
        }
        log.info("MongoDB client closed.");
    }
}
