package com.backend.user.config;

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
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "spring.data.mongodb")
@Data
@EqualsAndHashCode(callSuper = false)
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
    @Primary
    public MongoClient mongoClient() {
        return createMongoClient(host, port, username, password, authenticationDatabase, options);
    }

    @Bean(name = "projectMongoTemplate")
    public MongoTemplate projectMongoTemplate() {
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(mongoClient(), "project"));
    }

    static MongoClient createMongoClient(String host, int port, String username, String password, String authDb, Options options) {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyToClusterSettings(clusterBuilder ->
                    clusterBuilder.hosts(Collections.singletonList(new ServerAddress(host, port)))
                              .serverSelectionTimeout(options.serverSelectionTimeout, TimeUnit.MILLISECONDS))
                .applyToConnectionPoolSettings(poolBuilder ->
                    poolBuilder.maxSize(options.maxConnectionPoolSize)
                              .minSize(options.minConnectionPoolSize))
                .applyToSocketSettings(socketBuilder ->
                    socketBuilder.connectTimeout(options.connectTimeout, TimeUnit.MILLISECONDS));

        if (username != null && !username.trim().isEmpty() &&
            password != null && !password.trim().isEmpty()) {
            MongoCredential credential = MongoCredential.createCredential(
                username, authDb, password.toCharArray());
            builder.credential(credential);
        }

        return MongoClients.create(builder.build());
    }
}
