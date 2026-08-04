package com.backend.cloudflare.config;

import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.host:192.168.86.9}")
    private String mongoHost;

    @Value("${spring.data.mongodb.port:27017}")
    private int mongoPort;

    @Value("${spring.data.mongodb.username:root}")
    private String mongoUsername;

    @Value("${spring.data.mongodb.password:root123}")
    private String mongoPassword;

    @Value("${spring.data.mongodb.authentication-database:admin}")
    private String authDb;

    @Bean(name = "cloudflareMongoTemplate")
    public MongoTemplate cloudflareMongoTemplate() {
        String uri = String.format("mongodb://%s:%s@%s:%d/cloudflare?authSource=%s",
                mongoUsername, mongoPassword, mongoHost, mongoPort, authDb);
        return new MongoTemplate(MongoClients.create(uri), "cloudflare");
    }

    @Bean(name = "domainMongoTemplate")
    public MongoTemplate domainMongoTemplate() {
        String uri = String.format("mongodb://%s:%s@%s:%d/domain?authSource=%s",
                mongoUsername, mongoPassword, mongoHost, mongoPort, authDb);
        return new MongoTemplate(MongoClients.create(uri), "domain");
    }
}