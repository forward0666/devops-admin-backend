package com.backend.cloudflare.config;

import org.springframework.stereotype.Component;

/**
 * Provides dynamic MongoDB collection names for CfZoneEntity.
 * Used via SpEL: #{@zoneCollectionNameProvider.getCollectionName(#root.accountId)}
 */
@Component("zoneCollectionNameProvider")
public class ZoneCollectionNameProvider {

    public String getCollectionName(String accountId) {
        return "account_" + accountId + "_zones";
    }
}