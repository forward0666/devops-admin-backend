package com.backend.manage.config;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoRepositories(basePackages = "com.backend.manage.repository.mongo")
@ConfigurationProperties(prefix = "spring.data.mongodb")
@Data
@EqualsAndHashCode(callSuper=false)
public class MongoConfig extends AbstractMongoClientConfiguration {

    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private String authenticationDatabase;
    private Options options = new Options();

    @Data
    public static class Options {
        private int connectTimeout = 10000;
        private int serverSelectionTimeout = 30000;
        private int maxConnectionPoolSize = 100;
        private int minConnectionPoolSize = 10;
    }

    @Override
    protected String getDatabaseName() {
        return database;
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyToClusterSettings(clusterBuilder -> 
                    clusterBuilder.hosts(Collections.singletonList(new ServerAddress(host, port)))
                              .serverSelectionTimeout(options.serverSelectionTimeout, TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(poolBuilder -> 
                    poolBuilder.maxSize(options.maxConnectionPoolSize)
                              .minSize(options.minConnectionPoolSize))
                .applyToSocketSettings(socketBuilder -> 
                    socketBuilder.connectTimeout(options.connectTimeout, TimeUnit.MILLISECONDS));

        // Add authentication if username and password are provided
        if (username != null && !username.trim().isEmpty() && 
            password != null && !password.trim().isEmpty()) {
            MongoCredential credential = MongoCredential.createCredential(
                username, authenticationDatabase, password.toCharArray());
            builder.credential(credential);
        }

        return MongoClients.create(builder.build());
    }
}
